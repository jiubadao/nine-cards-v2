package com.fortysevendeg.ninecardslauncher.app.ui.launcher.collection

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.{Fragment, FragmentManager}
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.ImageView
import com.fortysevendeg.macroid.extras.FragmentExtras._
import com.fortysevendeg.macroid.extras.ResourcesExtras._
import com.fortysevendeg.macroid.extras.TextTweaks._
import com.fortysevendeg.macroid.extras.ViewGroupTweaks._
import com.fortysevendeg.macroid.extras.ViewTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.AsyncImageTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.ExtraTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.PositionsUtils._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.SafeUi._
import com.fortysevendeg.ninecardslauncher.app.ui.commons._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.actions.{ActionsBehaviours, BaseActionFragment}
import com.fortysevendeg.ninecardslauncher.app.ui.components.layouts.tweaks.AnimatedWorkSpacesTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.components.layouts.tweaks.LauncherWorkSpacesTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.components.layouts.{LauncherWorkSpaces, WorkSpaceItemMenu}
import com.fortysevendeg.ninecardslauncher.app.ui.launcher.LauncherTags
import com.fortysevendeg.ninecardslauncher.app.ui.launcher.Snails._
import com.fortysevendeg.ninecardslauncher.app.ui.launcher.actions.newcollection.NewCollectionFragment
import com.fortysevendeg.ninecardslauncher.app.ui.launcher.actions.privatecollections.PrivateCollectionsFragment
import com.fortysevendeg.ninecardslauncher.app.ui.launcher.actions.publicollections.PublicCollectionsFragment
import com.fortysevendeg.ninecardslauncher.process.collection.models._
import com.fortysevendeg.ninecardslauncher.process.device.models.DockApp
import com.fortysevendeg.ninecardslauncher.process.theme.models.NineCardsTheme
import com.fortysevendeg.ninecardslauncher.process.userconfig.models.UserInfo
import com.fortysevendeg.ninecardslauncher2.{R, TR, TypedFindView}
import macroid.FullDsl._
import macroid._

import scala.concurrent.ExecutionContext.Implicits.global

trait CollectionsComposer
  extends Styles
  with ActionsBehaviours
  with LauncherExecutor {

  self: AppCompatActivity with TypedFindView with SystemBarsTint =>

  // TODO We select the page in ViewPager with collections. In the future this will be a user preference
  val selectedPageDefault = 1

  val pageWidgets = 0

  val pageCollections = 1

  var workspaces: Option[LauncherWorkSpaces] = None

  lazy val drawerLayout = Option(findView(TR.launcher_drawer_layout))

  lazy val navigationView = Option(findView(TR.launcher_navigation_view))

  lazy val menuName = Option(findView(TR.menu_name))

  lazy val menuAvatar = Option(findView(TR.menu_avatar))

  lazy val loading = Option(findView(TR.launcher_loading))

  lazy val content = Option(findView(TR.launcher_content))

  lazy val workspacesContent = Option(findView(TR.launcher_work_spaces_content))

  lazy val appDrawerPanel = Option(findView(TR.launcher_drawer_panel))

  lazy val appDrawer1 = Option(findView(TR.launcher_page_1))

  lazy val appDrawer2 = Option(findView(TR.launcher_page_2))

  lazy val appDrawer3 = Option(findView(TR.launcher_page_3))

  lazy val appDrawer4 = Option(findView(TR.launcher_page_4))

  lazy val paginationPanel = Option(findView(TR.launcher_pagination_panel))

  lazy val searchPanel = Option(findView(TR.launcher_search_panel))

  lazy val burgerIcon = Option(findView(TR.launcher_burger_icon))

  lazy val googleIcon = Option(findView(TR.launcher_google_icon))

  lazy val micIcon = Option(findView(TR.launcher_mic_icon))

  lazy val actionFragmentContent = Option(findView(TR.action_fragment_content))

  lazy val menuCollectionRoot = Option(findView(TR.menu_collection_root))

  lazy val menuCollectionContent = Option(findView(TR.menu_collection_content))

  var dockApps: Seq[DockApp] = Seq.empty

  def showMessage(message: Int): Ui[_] = drawerLayout <~ uiSnackbarShort(message)

  def updateBarsInFabMenuShow: Ui[_] = Ui.nop

  def updateBarsInFabMenuHide: Ui[_] = Ui.nop

  def showLoading(implicit context: ActivityContextWrapper): Ui[_] = loading <~ vVisible

  def createCollections(
    collections: Seq[Collection],
    apps: Seq[DockApp])
    (implicit context: ActivityContextWrapper, uiContext: UiContext[_], theme: NineCardsTheme): Ui[_] = {
    dockApps = apps
    (loading <~ vGone) ~
      (workspaces <~
        lwsData(collections, selectedPageDefault) <~
        awsAddPageChangedObserver(currentPage => {
          runUi(paginationPanel <~ reloadPager(currentPage))
        }
        )) ~
      (appDrawerPanel <~ fillAppDrawer) ~
      createPager(selectedPageDefault)
  }

  def userInfoMenu(userInfo: UserInfo)(implicit uiContext: UiContext[_]): Ui[_] =
    (menuName <~ tvText(userInfo.email)) ~
      (menuAvatar <~ ivUri(userInfo.imageUrl))

  def uiActionCollection(action: UiAction, collection: Collection)
    (implicit context: ActivityContextWrapper, theme: NineCardsTheme): Ui[_] =
    action match {
      case Add => (workspaces <~ lwsAddCollection(collection)) ~ reloadPagerAndActiveLast
      case Remove => (workspaces <~ lwsRemoveCollection(collection)) ~ reloadPagerAndActiveLast
    }

  def closeMenu(): Ui[_] = drawerLayout <~ dlCloseDrawer

  def closeCollectionMenu(): Ui[_] = workspaces <~ lwsCloseMenu

  def isMenuVisible: Boolean = drawerLayout exists (_.isDrawerOpen(GravityCompat.START))

  def isCollectionMenuVisible: Boolean = workspaces exists (_.workSpacesStatuses.openedMenu)

  def goToWorkspace(page: Int)(implicit context: ActivityContextWrapper, theme: NineCardsTheme): Ui[_] =
    (workspaces <~ lwsSelect(page)) ~
      (paginationPanel <~ reloadPager(page)) ~
      closeMenu()

  protected def goToMenuOption(itemId: Int)
    (implicit context: ActivityContextWrapper, theme: NineCardsTheme): Ui[_] = itemId match {
    case R.id.menu_collections => goToWorkspace(pageCollections)
    case R.id.menu_moments => goToWorkspace(pageWidgets)
    case R.id.menu_profile => showMessage(R.string.todo)
    case R.id.menu_wallpapers => uiStartIntent(new Intent(Intent.ACTION_SET_WALLPAPER))
    case R.id.menu_android_settings => uiStartIntent(new Intent(android.provider.Settings.ACTION_SETTINGS))
    case R.id.menu_9cards_settings => showMessage(R.string.todo)
    case R.id.menu_widgets => showMessage(R.string.todo)
    case _ => Ui.nop
  }

  protected def clickAppDrawerItem(view: View)(implicit context: ActivityContextWrapper): Ui[_] = Ui {
    val position = Int.unbox(view.getTag(R.id.app_drawer_position))
    dockApps.lift(position) foreach { app =>
      execute(app.intent)
    }
  }

  protected def getItemsForFabMenu(implicit context: ActivityContextWrapper, theme: NineCardsTheme, managerContext: FragmentManagerContext[Fragment, FragmentManager]) = Seq(
    getUi(w[WorkSpaceItemMenu] <~ workspaceButtonCreateCollectionStyle <~ FuncOn.click { view: View =>
      showAction(f[NewCollectionFragment], view, resGetColor(R.color.collection_fab_button_item_create_new_collection))
    }),
    getUi(w[WorkSpaceItemMenu] <~ workspaceButtonMyCollectionsStyle <~ FuncOn.click { view: View =>
      showAction(f[PrivateCollectionsFragment], view, resGetColor(R.color.collection_fab_button_item_my_collections))
    }),
    getUi(w[WorkSpaceItemMenu] <~ workspaceButtonPublicCollectionStyle <~ FuncOn.click { view: View =>
      showAction(f[PublicCollectionsFragment], view, resGetColor(R.color.collection_fab_button_item_public_collection))
    })
  )

  private[this] def createPager(activatePosition: Int)(implicit context: ActivityContextWrapper, theme: NineCardsTheme) =
    workspaces map { ws =>
      val pagerViews = 0 until ws.getWorksSpacesCount map { position =>
        val view = pagination(position)
        view.setActivated(activatePosition == position)
        view
      }
      paginationPanel <~ vgAddViews(pagerViews)
    } getOrElse Ui.nop

  private[this] def reloadPagerAndActiveLast(implicit context: ActivityContextWrapper, theme: NineCardsTheme) =
    workspaces map { ws =>
      val count = ws.getWorksSpacesCount
      val pagerViews = 0 until count map { position =>
        val view = pagination(position)
        view.setActivated(count - 1 == position)
        view
      }
      paginationPanel <~ vgRemoveAllViews <~ vgAddViews(pagerViews)
    } getOrElse Ui.nop

  private[this] def fillAppDrawer(implicit context: ActivityContextWrapper, uiContext: UiContext[_], theme: NineCardsTheme) = Transformer {
    case i: ImageView if tagEquals(i, R.id.`type`, LauncherTags.app) =>
      val position = Int.unbox(i.getTag(R.id.app_drawer_position))
      val dockApp = dockApps(position)
      i <~ ivUri(dockApp.imagePath)
  }

  def reloadPager(currentPage: Int)(implicit context: ActivityContextWrapper, theme: NineCardsTheme) = Transformer {
    case i: ImageView if Option(i.getTag).isDefined && i.getTag.equals(currentPage.toString) => i <~ vActivated(true) <~~ pagerAppear
    case i: ImageView => i <~ vActivated(false)
  }

  def pagination(position: Int)(implicit context: ActivityContextWrapper, theme: NineCardsTheme) = getUi(
    w[ImageView] <~ paginationItemStyle <~ vTag(position.toString)
  )

  private[this] def tagEquals(view: View, id: Int, value: String) =
    Option(view.getTag(id)).isDefined && view.getTag(id).equals(value)

  private[this] def showAction[F <: BaseActionFragment]
  (fragmentBuilder: FragmentBuilder[F], view: View, color: Int, map: Map[String, String] = Map.empty)
    (implicit context: ActivityContextWrapper, managerContext: FragmentManagerContext[Fragment, FragmentManager]): Ui[_] = {
    val sizeIconWorkSpaceMenuItem = resGetDimensionPixelSize(R.dimen.size_workspace_menu_item)
    val (startX: Int, startY: Int) = Option(view.findViewById(R.id.workspace_icon)) map calculateAnchorViewPosition getOrElse(0, 0)
    val x = startX + (sizeIconWorkSpaceMenuItem / 2)
    val y = startY + (sizeIconWorkSpaceMenuItem / 2)
    val args = new Bundle()
    args.putInt(BaseActionFragment.sizeIcon, sizeIconWorkSpaceMenuItem)
    args.putInt(BaseActionFragment.startRevealPosX, x)
    args.putInt(BaseActionFragment.startRevealPosY, y)
    args.putInt(BaseActionFragment.endRevealPosX, x)
    args.putInt(BaseActionFragment.endRevealPosY, y)
    map foreach {
      case (key, value) => args.putString(key, value)
    }
    args.putInt(BaseActionFragment.colorPrimary, color)
    (fragmentContent <~ vClickable(true)) ~
      addFragment(fragmentBuilder.pass(args), Option(R.id.action_fragment_content), Option(nameActionFragment))
  }

}