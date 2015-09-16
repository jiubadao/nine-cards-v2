package com.fortysevendeg.ninecardslauncher.app.ui.commons

import android.app.{Application, Activity}
import android.support.v4.app.Fragment

sealed trait UiContext[T] {
  val value: T
}

case class ApplicationUiContext(value: Application) extends UiContext[Application]

case class ActivityUiContext(value: Activity) extends UiContext[Activity]

case class FragmentUiContext(value: Fragment) extends UiContext[Fragment]