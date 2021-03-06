/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cards.nine.app.ui.commons.dialogs.editmoment

import android.app.Dialog
import cards.nine.app.commons.AppNineCardsIntentConversions
import cards.nine.app.ui.commons.dialogs.BaseActionFragment
import cards.nine.app.ui.commons.ops.TaskServiceOps._
import cards.nine.commons.javaNull
import cards.nine.models.types.NineCardsMoment
import cards.nine.models.{Moment, MomentTimeSlot}
import com.fortysevendeg.ninecardslauncher.R

class EditMomentFragment
    extends BaseActionFragment
    with EditMomentUiActions
    with EditMomentDOM
    with EditMomentListener
    with AppNineCardsIntentConversions { self =>

  lazy val momentType = Option(
    getString(Seq(getArguments), EditMomentFragment.momentKey, javaNull))

  lazy val editJobs = new EditMomentJobs(self)

  override def useFab: Boolean = true

  override def getLayoutId: Int = R.layout.edit_moment

  override def setupDialog(dialog: Dialog, style: Int): Unit = {
    super.setupDialog(dialog, style)
    momentType match {
      case Some(moment) =>
        editJobs.initialize(NineCardsMoment(moment)).resolveServiceOr(_ => close())
      case _ => editJobs.momentNotFound().resolveAsync()
    }
  }

  override def addWifi(): Unit =
    editJobs.addWifi().resolveAsyncServiceOr(_ => showFieldErrorMessage())

  override def addWifi(wifi: String): Unit =
    editJobs.addWifi(wifi).resolveAsync()

  override def addBluetooth(): Unit =
    editJobs.addBluetooth().resolveAsyncServiceOr(_ => showFieldErrorMessage())

  override def addBluetooth(device: String): Unit =
    editJobs.addBluetooth(device).resolveAsync()

  override def addHour(): Unit = editJobs.addHour().resolveAsync()

  override def saveMoment(): Unit =
    editJobs.saveMoment().resolveAsyncServiceOr(_ => showSavingMomentErrorMessage())

  override def setCollectionId(collectionId: Option[Int]): Unit =
    editJobs.setCollectionId(collectionId).resolveAsync()

  override def removeHour(position: Int): Unit =
    editJobs.removeHour(position).resolveAsync()

  override def removeWifi(position: Int): Unit =
    editJobs.removeWifi(position).resolveAsync()

  override def removeBluetooth(position: Int): Unit =
    editJobs.removeBluetooth(position).resolveAsync()

  override def changeFromHour(position: Int, hour: String): Unit =
    editJobs
      .changeFromHour(position, hour)
      .resolveAsyncServiceOr(_ => showSavingMomentErrorMessage())

  override def changeToHour(position: Int, hour: String): Unit =
    editJobs
      .changeToHour(position, hour)
      .resolveAsyncServiceOr(_ => showSavingMomentErrorMessage())

  override def swapDay(position: Int, index: Int): Unit =
    editJobs.swapDay(position, index).resolveAsyncServiceOr(_ => showSavingMomentErrorMessage())
}

object EditMomentFragment {

  val momentKey = "moment"

  var statuses = EditMomentStatuses()

}

case class EditMomentStatuses(moment: Option[Moment] = None, modifiedMoment: Option[Moment] = None) {

  def start(m: Moment): EditMomentStatuses =
    copy(moment = Option(m), modifiedMoment = Option(m))

  def setCollectionId(collectionId: Option[Int]): EditMomentStatuses =
    copy(modifiedMoment = modifiedMoment map (_.copy(collectionId = collectionId)))

  def swapDay(position: Int, day: Int): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      val timeslots = m.timeslot.zipWithIndex map {
        case (timeslot, index) if index == position =>
          val modDays = timeslot.days.zipWithIndex map {
            case (s, indexDay) if indexDay == day && s == 0 => 1
            case (s, indexDay) if indexDay == day           => 0
            case (s, indexDay)                              => s
          }
          timeslot.copy(days = modDays)
        case (timeslot, _) => timeslot
      }
      m.copy(timeslot = timeslots)
    }
    copy(modifiedMoment = modMoment)
  }

  def changeFromHour(position: Int, hour: String): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      val timeslots = m.timeslot.zipWithIndex map {
        case (timeslot, index) if index == position =>
          timeslot.copy(from = hour)
        case (timeslot, _) => timeslot
      }
      m.copy(timeslot = timeslots)
    }
    copy(modifiedMoment = modMoment)
  }

  def changeToHour(position: Int, hour: String): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      val timeslots = m.timeslot.zipWithIndex map {
        case (timeslot, index) if index == position => timeslot.copy(to = hour)
        case (timeslot, _)                          => timeslot
      }
      m.copy(timeslot = timeslots)
    }
    copy(modifiedMoment = modMoment)
  }

  def addHour(time: MomentTimeSlot): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      m.copy(timeslot = m.timeslot :+ time)
    }
    copy(modifiedMoment = modMoment)
  }

  def removeHour(position: Int): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      m.copy(timeslot = m.timeslot.filterNot(m.timeslot.indexOf(_) == position))
    }
    copy(modifiedMoment = modMoment)
  }

  def addWifi(wifi: String): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      m.copy(wifi = m.wifi :+ wifi)
    }
    copy(modifiedMoment = modMoment)
  }

  def removeWifi(position: Int): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      m.copy(wifi = m.wifi.filterNot(m.wifi.indexOf(_) == position))
    }
    copy(modifiedMoment = modMoment)
  }

  def addBluetooth(device: String): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      m.copy(bluetooth = m.bluetooth :+ device)
    }
    copy(modifiedMoment = modMoment)
  }

  def removeBluetooth(position: Int): EditMomentStatuses = {
    val modMoment = modifiedMoment map { m =>
      m.copy(bluetooth = m.bluetooth.filterNot(m.bluetooth.indexOf(_) == position))
    }
    copy(modifiedMoment = modMoment)
  }

  def wasModified(): Boolean = moment != modifiedMoment

}
