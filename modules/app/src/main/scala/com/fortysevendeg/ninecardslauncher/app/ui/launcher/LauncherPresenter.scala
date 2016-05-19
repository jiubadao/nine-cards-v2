package com.fortysevendeg.ninecardslauncher.app.ui.launcher

import android.app.Activity
import android.content.{Context, Intent}
import android.graphics.Point
import android.support.v7.app.AppCompatActivity
import com.fortysevendeg.macroid.extras.ResourcesExtras._
import com.fortysevendeg.ninecardslauncher.app.analytics._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.Constants._
import com.fortysevendeg.ninecardslauncher.app.commons.{Conversions, NineCardIntentConversions}
import com.fortysevendeg.ninecardslauncher.app.ui.collections.CollectionsDetailsActivity
import com.fortysevendeg.ninecardslauncher.app.ui.collections.CollectionsDetailsActivity._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.AppUtils._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.TasksOps._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.{LauncherExecutor, Presenter, RequestCodes}
import com.fortysevendeg.ninecardslauncher.app.ui.components.dialogs.AlertDialogFragment
import com.fortysevendeg.ninecardslauncher.app.ui.components.layouts.{CollectionsWorkSpace, LauncherData, MomentWorkSpace}
import com.fortysevendeg.ninecardslauncher.app.ui.launcher.Statuses._
import com.fortysevendeg.ninecardslauncher.app.ui.launcher.drawer._
import com.fortysevendeg.ninecardslauncher.app.ui.wizard.WizardActivity
import com.fortysevendeg.ninecardslauncher.commons._
import com.fortysevendeg.ninecardslauncher.commons.services.Service._
import com.fortysevendeg.ninecardslauncher.process.collection.{AddCardRequest, CollectionException}
import com.fortysevendeg.ninecardslauncher.process.commons.models.Collection
import com.fortysevendeg.ninecardslauncher.process.commons.types._
import com.fortysevendeg.ninecardslauncher.process.device._
import com.fortysevendeg.ninecardslauncher.process.device.models._
import com.fortysevendeg.ninecardslauncher.process.user.UserException
import com.fortysevendeg.ninecardslauncher.process.user.models.User
import com.fortysevendeg.ninecardslauncher2.R
import com.fortysevendeg.ninecardslauncher.commons.ops.SeqOps._
import macroid.{ActivityContextWrapper, Ui}

import scala.annotation.tailrec
import scala.concurrent.Future
import scalaz.concurrent.Task

class LauncherPresenter(actions: LauncherUiActions)(implicit contextWrapper: ActivityContextWrapper)
  extends Presenter
  with Conversions
  with NineCardIntentConversions
  with LauncherExecutor
  with AnalyticDispatcher { self =>

  val tagDialog = "dialog"

  val defaultPage = 1

  var statuses = LauncherPresenterStatuses()

  override def getApplicationContext: Context = contextWrapper.application

  def initialize(): Unit = {
    Task.fork(di.userProcess.register.run).resolveAsync()
    actions.initialize.run
  }

  def resume(): Unit = {
    di.observerRegister.registerObserver()
    if (actions.isEmptyCollectionsInWorkspace) {
      loadCollectionsAndDockApps()
    }
  }

  def pause(): Unit = di.observerRegister.unregisterObserver()

  def back(): Unit = actions.back.run

  def resetAction(): Unit = actions.resetAction.run

  def destroyAction(): Unit = actions.destroyAction.run

  def logout(): Unit = actions.logout.run

  def startAddItemToCollection(app: App): Unit = startAddItemToCollection(toAddCardRequest(app))

  def startAddItemToCollection(contact: Contact): Unit = startAddItemToCollection(toAddCardRequest(contact))

  private[this] def startAddItemToCollection(addCardRequest: AddCardRequest): Unit = {
    statuses = statuses.startAddItem(addCardRequest)
    actions.startAddItem(addCardRequest.cardType).run
  }

  def draggingAddItemTo(position: Int): Unit = statuses = statuses.updateCurrentPosition(position)

  def draggingAddItemToPreviousScreen(position: Int): Unit = {
    actions.goToPreviousScreenAddingItem().run
    statuses.updateCurrentPosition(position)
  }

  def draggingAddItemToNextScreen(position: Int): Unit = {
    actions.goToNextScreenAddingItem().run
    statuses.updateCurrentPosition(position)
  }

  def endAddItemToCollection(): Unit = {
    (actions.getCollection(statuses.currentDraggingPosition), statuses.cardAddItemMode) match {
      case (Some(collection: Collection), Some(request: AddCardRequest)) =>
        Task.fork(di.collectionProcess.addCards(collection.id, Seq(request)).run).resolveAsyncUi(
          onResult = (_) => actions.showAddItemMessage(collection.name),
          onException = (_) => actions.showContactUsError())
      case _ =>
    }
    statuses = statuses.reset()
    actions.endAddItem.run
  }

  def endAddItemToDockApp(position: Int): Unit = {
    statuses.cardAddItemMode match {
      case Some(card: AddCardRequest) =>
        card.cardType match {
          case AppCardType =>
            Task.fork(createOrUpdateDockApp(card, AppDockType, position).run).resolveAsyncUi(
              onResult = (_) => actions.reloadDockApps(DockApp(card.term, AppDockType, card.intent, card.imagePath, position)),
              onException = (_) => actions.showContactUsError())
          case ContactCardType =>
            Task.fork(createOrUpdateDockApp(card, ContactDockType, position).run).resolveAsyncUi(
              onResult = (_) => actions.reloadDockApps(DockApp(card.term, ContactDockType, card.intent, card.imagePath, position)),
              onException = (_) => actions.showContactUsError())
          case _ =>
            actions.showContactUsError()
        }
      case _ =>
        actions.showContactUsError().run
    }
    statuses = statuses.reset()
    actions.endAddItem.run
  }

  def endAddItem(): Unit = if (statuses.mode == AddItemMode) {
    statuses = statuses.reset()
    actions.endAddItem.run
  }

  def uninstallInAddItem(): Unit = {
    statuses.cardAddItemMode match {
      case Some(card: AddCardRequest) if card.cardType == AppCardType =>
        card.packageName foreach launchUninstall
      case _ =>
    }
    statuses = statuses.reset()
    actions.endAddItem.run
  }

  def settingsInAddItem(): Unit = {
    statuses.cardAddItemMode match {
      case Some(card: AddCardRequest) if card.cardType == AppCardType =>
        card.packageName foreach launchSettings
      case _ =>
    }
    statuses = statuses.reset()
    actions.endAddItem.run
  }

  def startReorder(maybeCollection: Option[Collection], position: Int): Unit = {
    maybeCollection map { collection =>
      statuses = statuses.startReorder(collection, position)
      actions.startReorder.run
    } getOrElse {
      actions.showContactUsError().run
    }
  }

  def draggingReorderTo(position: Int): Unit = statuses = statuses.updateCurrentPosition(position)

  def draggingReorderToNextScreen(position: Int): Unit = {
    actions.goToNextScreenReordering().run
    statuses = statuses.updateCurrentPosition(position)
  }

  def draggingReorderToPreviousScreen(position: Int): Unit = {
    actions.goToPreviousScreenReordering().run
    statuses = statuses.updateCurrentPosition(position)
  }

  def dropReorder(): Unit = if (statuses.mode == ReorderMode) {
    actions.endReorder.run
    val from = statuses.startPositionReorderMode
    val to = statuses.currentDraggingPosition
    if (from != to) {
      Task.fork(di.collectionProcess.reorderCollection(from, to).run).resolveAsyncUi(
        onResult = (_) => {
          val (page, data) = reorderCollectionsInCurrentData(from, to)
          actions.reloadWorkspaces(page, data)
        },
        onException = (_) => {
          val (page, data) = reloadCollectionsInCurrentData
          actions.reloadWorkspaces(page, data) ~ actions.showContactUsError()
        })
    } else {
      val (page, data) = reloadCollectionsInCurrentData
      actions.reloadWorkspaces(page, data).run
    }
    statuses = statuses.reset()
  }

  def removeCollectionInReorderMode(): Unit =
    (statuses.collectionReorderMode map { collection =>
      if (actions.canRemoveCollections) {
        Ui(showDialogForRemoveCollection(collection))
      } else {
        actions.showMinimumOneCollectionMessage()
      }
    } getOrElse actions.showContactUsError()).run

  def editCollectionInReorderMode(): Unit =
    (statuses.collectionReorderMode match {
      case Some(_) => actions.showNoImplementedYetMessage()
      case None => actions.showContactUsError()
    }).run

  def openApp(app: App): Unit = if (actions.isTabsOpened) {
    actions.closeTabs.run
  } else {
    self !>>
      TrackEvent(
        screen = CollectionDetailScreen,
        category = AppCategory(app.category),
        action = OpenAction,
        label = Some(ProvideLabel(app.packageName)),
        value = Some(OpenAppFromAppDrawerValue))
    execute(toNineCardIntent(app))
  }

  def openContact(contact: Contact) = if (actions.isTabsOpened) {
    actions.closeTabs.run
  } else {
    executeContact(contact.lookupKey)
  }

  def openLastCall(contact: LastCallsContact) = if (actions.isTabsOpened) {
    actions.closeTabs.run
  } else {
    execute(phoneToNineCardIntent(contact.number))
  }

  def addCollection(collection: Collection): Unit = {
    addCollectionToCurrentData(collection) match {
      case Some((page: Int, data: Seq[LauncherData])) =>
        (actions.reloadWorkspaces(page, data) ~ actions.reloadPagerInAddCollection()).run
      case _ =>
    }
  }

  def removeCollection(collection: Collection): Unit = {
    Task.fork(deleteCollection(collection.id).run).resolveAsyncUi(
      onResult = (_) => {
        val (page, data) = removeCollectionToCurrentData(collection.id)
        actions.reloadWorkspaces(page, data)
      },
      onException = (_) => actions.showContactUsError()
    )
  }

  def loadCollectionsAndDockApps(): Unit = {
    Task.fork(getLauncherApps.run).resolveAsyncUi(
      onResult = {
        // Check if there are collections in DB, if there aren't we go to wizard
        case (Nil, _) => Ui(goToWizard())
        case (collections, apps) =>
          Task.fork(getUser.run).resolveAsyncUi(
            onResult = user => actions.showUserProfile(
              email = user.email,
              name = user.userProfile.name,
              avatarUrl = user.userProfile.avatar,
              coverPhotoUrl = user.userProfile.cover))
          val data = LauncherData(MomentWorkSpace) +: getCollectionsItems(collections, Seq.empty, LauncherData(CollectionsWorkSpace))
          actions.loadCollections(data, apps)
      },
      onException = (ex: Throwable) => Ui(goToWizard()),
      onPreTask = () => actions.showLoading()
    )
  }

  def loadApps(appsMenuOption: AppsMenuOption): Unit = {
    val getAppOrder = toGetAppOrder(appsMenuOption)
    Task.fork(getLoadApps(getAppOrder).run).resolveAsyncUi(
      onResult = {
        case (apps: IterableApps, counters: Seq[TermCounter]) =>
          actions.reloadAppsInDrawer(
            apps = apps,
            getAppOrder = getAppOrder,
            counters = counters)
      }
    )
  }

  def loadContacts(contactsMenuOption: ContactsMenuOption): Unit = {
    contactsMenuOption match {
      case ContactsByLastCall =>
        Task.fork(di.deviceProcess.getLastCalls.run).resolveAsyncUi(
          onResult = (contacts: Seq[LastCallsContact]) => actions.reloadLastCallContactsInDrawer(contacts))
      case _ =>
        val getContactFilter = toGetContactFilter(contactsMenuOption)
        Task.fork(getLoadContacts(getContactFilter).run).resolveAsyncUi(
          onResult = {
            case (contacts: IterableContacts, counters: Seq[TermCounter]) =>
              actions.reloadContactsInDrawer(contacts = contacts, counters = counters)
          })
    }
  }

  def loadAppsByKeyword(keyword: String): Unit = {
    Task.fork(di.deviceProcess.getIterableAppsByKeyWord(keyword, GetByName).run).resolveAsyncUi(
      onResult = {
        case (apps: IterableApps) => actions.reloadAppsInDrawer(apps = apps)
      })
  }

  def loadContactsByKeyword(keyword: String): Unit = {
    Task.fork(di.deviceProcess.getIterableContactsByKeyWord(keyword).run).resolveAsyncUi(
      onResult = {
        case (contacts: IterableContacts) => actions.reloadContactsInDrawer(contacts = contacts)
      })
  }

  def goToCollection(maybeCollection: Option[Collection], point: Point): Unit = {
    def launchIntent(activity: Activity, collection: Collection) = {
      val intent = new Intent(activity, classOf[CollectionsDetailsActivity])
      intent.putExtra(startPosition, collection.position)
      intent.putExtra(indexColorToolbar, collection.themedColorIndex)
      intent.putExtra(iconToolbar, collection.icon)
      val color = resGetColor(getIndexColor(collection.themedColorIndex))
      actions.rippleToCollection(color, point) ~~
        Ui {
          activity.startActivityForResult(intent, RequestCodes.goToCollectionDetails)
        }
    }

    ((for {
      collection <- maybeCollection
      activity <- contextWrapper.original.get
    } yield launchIntent(activity, collection)) getOrElse actions.showContactUsError()).run
  }

  def resetFromCollectionDetail(): Unit = actions.resetFromCollection().run

  def goToWizard(): Unit = {
    contextWrapper.original.get foreach { activity =>
      val wizardIntent = new Intent(activity, classOf[WizardActivity])
      activity.startActivity(wizardIntent)
    }
  }

  private[this] def createOrUpdateDockApp(card: AddCardRequest, dockType: DockType, position: Int) =
    di.deviceProcess.createOrUpdateDockApp(card.term, dockType, card.intent, card.imagePath, position)

  protected def deleteCollection(id: Int): ServiceDef2[Unit, CollectionException] =
    di.collectionProcess.deleteCollection(id)

  protected def getUser: ServiceDef2[User, UserException] = di.userProcess.getUser

  protected def getLauncherApps: ServiceDef2[(Seq[Collection], Seq[DockApp]), CollectionException with DockAppException] =
    for {
      collections <- di.collectionProcess.getCollections
      dockApps <- di.deviceProcess.getDockApps
    } yield (collections, dockApps)

  protected def getLoadApps(order: GetAppOrder): ServiceDef2[(IterableApps, Seq[TermCounter]), AppException] =
    for {
      iterableApps <- di.deviceProcess.getIterableApps(order)
      counters <- di.deviceProcess.getTermCountersForApps(order)
    } yield (iterableApps, counters)

  protected def getLoadContacts(order: ContactsFilter): ServiceDef2[(IterableContacts, Seq[TermCounter]), ContactException] =
    for {
      iterableContacts <- di.deviceProcess.getIterableContacts(order)
      counters <- di.deviceProcess.getTermCountersForContacts(order)
    } yield (iterableContacts, counters)

  private[this] def toGetAppOrder(appsMenuOption: AppsMenuOption): GetAppOrder = appsMenuOption match {
    case AppsAlphabetical => GetByName
    case AppsByCategories => GetByCategory
    case AppsByLastInstall => GetByInstallDate
  }

  private[this] def toGetContactFilter(contactMenuOption: ContactsMenuOption): ContactsFilter = contactMenuOption match {
    case ContactsFavorites => FavoriteContacts
    case _ => AllContacts
  }

  private[this] def showDialogForRemoveCollection(collection: Collection): Unit = {
    contextWrapper.original.get match {
      case Some(activity: AppCompatActivity) =>
        val ft = activity.getSupportFragmentManager.beginTransaction()
        Option(activity.getSupportFragmentManager.findFragmentByTag(tagDialog)) foreach ft.remove
        ft.addToBackStack(javaNull)
        val dialog = new AlertDialogFragment(
          message = R.string.removeCollectionMessage,
          positiveAction = () => removeCollection(collection)
        )
        dialog.show(ft, tagDialog)
      case _ =>
    }
  }

  private[this] def removeCollectionToCurrentData(collectionId: Int): (Int, Seq[LauncherData]) = {
    val currentData = actions.getData

    // We remove a collection in sequence and fix positions
    val collections = (currentData flatMap (_.collections.filterNot(_.id == collectionId))).zipWithIndex map {
      case (col, index) => col.copy(position = index)
    }

    val maybeWorkspaceCollection = currentData find (_.collections.exists(_.id == collectionId))
    val maybePage = maybeWorkspaceCollection map currentData.indexOf

    val page = maybePage map { page =>
      if (currentData.isDefinedAt(page)) page else currentData.length - 1
    } getOrElse defaultPage

    val newData = LauncherData(MomentWorkSpace) +: getCollectionsItems(collections, Seq.empty, LauncherData(CollectionsWorkSpace))

    (page, newData)
  }

  private[this] def reorderCollectionsInCurrentData(from: Int, to: Int): (Int, Seq[LauncherData]) = {
    val currentData = actions.getData
    val cols = currentData flatMap (_.collections)
    val collections = cols.reorder(from, to).zipWithIndex map {
      case (collection, index) => collection.copy(position = index)
    }
    val newData = LauncherData(MomentWorkSpace) +: getCollectionsItems(collections, Seq.empty, LauncherData(CollectionsWorkSpace))

    val page = (for {
      currentPage <- actions.getCurrentPage
      _ <- currentData.lift(currentPage)
    } yield currentPage) getOrElse defaultPage

    (page, newData)
  }

  private[this] def reloadCollectionsInCurrentData: (Int, Seq[LauncherData]) = {
    val currentData = actions.getData
    val collections = currentData flatMap (_.collections)
    val newData = LauncherData(MomentWorkSpace) +: getCollectionsItems(collections, Seq.empty, LauncherData(CollectionsWorkSpace))
    val page = (for {
      currentPage <- actions.getCurrentPage
      _ <- currentData.lift(currentPage)
    } yield currentPage) getOrElse defaultPage
    (page, newData)
  }

  private[this] def addCollectionToCurrentData(collection: Collection): Option[(Int, Seq[LauncherData])] = {
    val currentData = actions.getData
    currentData.lastOption map { data =>
      val lastWorkspaceHasSpace = data.collections.size < numSpaces
      val newData = if (lastWorkspaceHasSpace) {
        currentData.dropRight(1) :+ data.copy(collections = data.collections :+ collection)
      } else {
        val newPosition = currentData.count(_.workSpaceType == CollectionsWorkSpace)
        currentData :+ LauncherData(CollectionsWorkSpace, Seq(collection), newPosition)
      }
      val page = newData.size - 1
      (page, newData)
    }
  }

  // We create a new page every 9 collections
  @tailrec
  private[this] def getCollectionsItems(collections: Seq[Collection], acc: Seq[LauncherData], newLauncherData: LauncherData): Seq[LauncherData] = {
    def updatePositions(data: Seq[LauncherData]) = data.zipWithIndex map {
      case (d, index) => d.copy(positionByType = index)
    }
    collections match {
      case Nil if newLauncherData.collections.nonEmpty =>
        updatePositions(acc :+ newLauncherData)
      case Nil =>
        updatePositions(acc)
      case h :: t if newLauncherData.collections.length == numSpaces =>
        getCollectionsItems(t, acc :+ newLauncherData, LauncherData(CollectionsWorkSpace, Seq(h)))
      case h :: t =>
        val g: Seq[Collection] = newLauncherData.collections :+ h
        val n = LauncherData(CollectionsWorkSpace, g)
        getCollectionsItems(t, acc, n)
    }
  }

}

trait LauncherUiActions {

  def initialize: Ui[Any]

  def back: Ui[Any]

  def resetAction: Ui[Any]

  def destroyAction: Ui[Any]

  def logout: Ui[Any]

  def closeTabs: Ui[Any]

  def startReorder: Ui[Any]

  def endReorder: Ui[Any]

  def goToPreviousScreenReordering(): Ui[Any]

  def goToNextScreenReordering(): Ui[Any]

  def startAddItem(cardType: CardType): Ui[Any]

  def endAddItem: Ui[Any]

  def goToPreviousScreenAddingItem(): Ui[Any]

  def goToNextScreenAddingItem(): Ui[Any]

  def showUserProfile(email: Option[String], name: Option[String], avatarUrl: Option[String], coverPhotoUrl: Option[String]): Ui[Any]

  def reloadPagerInAddCollection(): Ui[Any]

  def reloadWorkspaces(page: Int, data: Seq[LauncherData]): Ui[Any]

  def reloadDockApps(dockApp: DockApp): Ui[Any]

  def showAddItemMessage(nameCollection: String): Ui[Any]

  def showContactUsError(): Ui[Any]

  def showMinimumOneCollectionMessage(): Ui[Any]

  def showNoImplementedYetMessage(): Ui[Any]

  def showLoading(): Ui[Any]

  def goToPreviousScreen(): Ui[Any]

  def goToNextScreen(): Ui[Any]

  def loadCollections(data: Seq[LauncherData], apps: Seq[DockApp]): Ui[Any]

  def reloadAppsInDrawer(
    apps: IterableApps,
    getAppOrder: GetAppOrder = GetByName,
    counters: Seq[TermCounter] = Seq.empty): Ui[Any]

  def reloadContactsInDrawer(
    contacts: IterableContacts,
    counters: Seq[TermCounter] = Seq.empty): Ui[_]

  def reloadLastCallContactsInDrawer(contacts: Seq[LastCallsContact]): Ui[Any]

  def rippleToCollection(color: Int, point: Point): Ui[Future[Any]]

  def resetFromCollection(): Ui[Any]

  def isEmptyCollectionsInWorkspace: Boolean

  def canRemoveCollections: Boolean

  def getCollection(position: Int): Option[Collection]

  def isTabsOpened: Boolean

  def getData: Seq[LauncherData]

  def getCurrentPage: Option[Int]

}

object Statuses {
  case class LauncherPresenterStatuses(
    mode: LauncherMode = NormalMode,
    cardAddItemMode: Option[AddCardRequest] = None,
    collectionReorderMode: Option[Collection] = None,
    startPositionReorderMode: Int = 0,
    currentDraggingPosition: Int = 0) {

    def startAddItem(card: AddCardRequest): LauncherPresenterStatuses =
      copy(mode = AddItemMode, cardAddItemMode = Some(card))

    def startReorder(collection: Collection, position: Int): LauncherPresenterStatuses =
      copy(
        startPositionReorderMode = position,
        collectionReorderMode = Some(collection),
        currentDraggingPosition = position,
        mode = ReorderMode)

    def updateCurrentPosition(position: Int): LauncherPresenterStatuses =
      copy(currentDraggingPosition = position)

    def reset(): LauncherPresenterStatuses =
      copy(
        startPositionReorderMode = 0,
        cardAddItemMode = None,
        collectionReorderMode = None,
        currentDraggingPosition = 0,
        mode = NormalMode)

    def isReordering(): Boolean = mode == ReorderMode

  }

  sealed trait LauncherMode

  case object NormalMode extends LauncherMode

  case object AddItemMode extends LauncherMode

  case object ReorderMode extends LauncherMode
}