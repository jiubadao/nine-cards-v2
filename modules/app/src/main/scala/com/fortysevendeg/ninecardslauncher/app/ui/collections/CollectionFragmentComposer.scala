package com.fortysevendeg.ninecardslauncher.app.ui.collections

import android.support.v4.app.Fragment
import android.support.v7.widget.{DefaultItemAnimator, CardView, GridLayoutManager, RecyclerView}
import android.widget.{FrameLayout, ImageView, LinearLayout, TextView}
import com.fortysevendeg.macroid.extras.RecyclerViewTweaks._
import com.fortysevendeg.macroid.extras.ResourcesExtras._
import com.fortysevendeg.macroid.extras.TextTweaks._
import com.fortysevendeg.macroid.extras.UIActionsExtras._
import com.fortysevendeg.macroid.extras.ViewTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.Constants._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.RecyclerViewListenerTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.components.NineRecyclerViewTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.components.PullToCloseViewTweaks._
import com.fortysevendeg.ninecardslauncher.app.ui.components.{NineRecyclerView, PullToCloseListener, PullToCloseView}
import com.fortysevendeg.ninecardslauncher.process.collection.models.{Collection, _}
import com.fortysevendeg.ninecardslauncher.process.theme.models.NineCardsTheme
import com.fortysevendeg.ninecardslauncher2.R
import macroid.FullDsl._
import macroid._

trait CollectionFragmentComposer
  extends CollectionFragmentStyles {

  var sType = -1

  var canScroll = false

  var activeFragment = false

  var scrolledListener: Option[ScrolledListener] = None

  var recyclerView = slot[NineRecyclerView]

  var pullToCloseView = slot[PullToCloseView]

  def layout(implicit contextWrapper: ActivityContextWrapper) = getUi(
    l[PullToCloseView](
      w[NineRecyclerView] <~ wire(recyclerView) <~ recyclerStyle
    ) <~ wire(pullToCloseView) <~ pcvListener(PullToCloseListener(
      startPulling = () => runUi(recyclerView <~ nrvDisableScroll(true)),
      endPulling = () => runUi(recyclerView <~ nrvDisableScroll(false)),
      scroll = (scroll: Int, close: Boolean) => scrolledListener foreach (_.pullToClose(scroll, sType, close)),
      close = () => scrolledListener foreach (_.close())
    ))
  )

  def initUi(collection: Collection)(implicit contextWrapper: ActivityContextWrapper, fragment: Fragment, theme: NineCardsTheme) =
    recyclerView <~ vGlobalLayoutListener(view => {
      val spaceMove = resGetDimensionPixelSize(R.dimen.space_moving_collection_details)
      val padding = resGetDimensionPixelSize(R.dimen.padding_small)
      val heightCard = (view.getHeight - (padding + spaceMove)) / numInLine
      loadCollection(collection, heightCard, padding, spaceMove) ~
        uiHandler(startScroll(padding, spaceMove))
    })

  def resetScroll(collection: Collection, position: Int)(implicit contextWrapper: ActivityContextWrapper) =
    recyclerView <~
      getScrollListener(collection, resGetDimensionPixelSize(R.dimen.space_moving_collection_details))

  def loadCollection(collection: Collection, heightCard: Int, padding: Int, spaceMove: Int)
    (implicit contextWrapper: ActivityContextWrapper, fragment: Fragment, theme: NineCardsTheme): Ui[_] = {
    val adapter = new CollectionAdapter(collection, heightCard)
    recyclerView <~ rvLayoutManager(new GridLayoutManager(contextWrapper.application, numInLine)) <~
      rvFixedSize <~
      rvAdapter(adapter) <~
      rvAddItemDecoration(new CollectionItemDecorator) <~
      rvItemAnimator(new DefaultItemAnimator) <~
      getScrollListener(collection, spaceMove)
  }

  private def getScrollListener(collection: Collection, spaceMove: Int)(implicit contextWrapper: ActivityContextWrapper) =
    rvCollectionScrollListener(
      scrolled = (scrollY: Int, dx: Int, dy: Int) => {
        val sy = scrollY + dy
        if (activeFragment && collection.cards.length > numSpaces) {
          scrolledListener foreach (_.scrollY(sy, dy))
        }
        sy
      },
      scrollStateChanged = (scrollY: Int, recyclerView: RecyclerView, newState: Int) => {
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
          scrolledListener foreach (_.startScroll())
        }
        if (activeFragment &&
          newState == RecyclerView.SCROLL_STATE_IDLE &&
          collection.cards.length > numSpaces) {
          scrolledListener foreach {
            sl =>
              val (moveTo, sType) = if (scrollY < spaceMove / 2) (0, ScrollType.down) else (spaceMove, ScrollType.up)
              (scrollY, moveTo, sType) match {
                case (y, move, st) if y < spaceMove && moveTo != scrollY =>
                  sl.scrollType(sType)
                  recyclerView.smoothScrollBy(0, moveTo - scrollY)
                case _ =>
              }
              sl.scrollType(sType)
          }
        }
      }
    )

  private[this] def startScroll(padding: Int, spaceMove: Int)(implicit contextWrapper: ContextWrapper): Ui[_] =
    (canScroll, sType) match {
      case (true, s) => recyclerView <~ vScrollBy(0, if (s == ScrollType.up) spaceMove else 0)
      case (_, s) => recyclerView <~ vPadding(padding, if (s == ScrollType.up) padding else spaceMove, padding, padding)
    }

  def scrollType(newSType: Int)(implicit contextWrapper: ContextWrapper): Ui[_] = {
    val spaceMove = resGetDimensionPixelSize(R.dimen.space_moving_collection_details)
    val padding = resGetDimensionPixelSize(R.dimen.padding_small)
    (canScroll, sType) match {
      case (scroll, s) if s != newSType && scroll =>
        sType = newSType
        recyclerView <~
          vScrollBy(0, -Int.MaxValue) <~
          (if (sType == ScrollType.up) vScrollBy(0, spaceMove) else Tweak.blank)
      case (_, s) if s != newSType =>
        sType = newSType
        recyclerView <~ vPadding(padding, if (newSType == ScrollType.up) padding else spaceMove, padding, padding)
      case _ => Ui.nop
    }
  }

  def getAdapter: Option[CollectionAdapter] = recyclerView flatMap { rv =>
    Option(rv.getAdapter) match {
      case Some(a: CollectionAdapter) => Some(a)
      case _ => None
    }
  }

}

class CollectionLayoutAdapter(heightCard: Int)(implicit context: ActivityContextWrapper, theme: NineCardsTheme)
  extends CollectionAdapterStyles {

  var iconContent = slot[FrameLayout]

  var icon = slot[ImageView]

  var name = slot[TextView]

  val content = layout

  private def layout(implicit context: ActivityContextWrapper) = getUi(
    l[CardView](
      l[LinearLayout](
        l[FrameLayout](
          w[ImageView] <~ wire(icon) <~ iconStyle
        ) <~ wire(iconContent) <~ iconContentStyle(heightCard),
        w[TextView] <~ wire(name) <~ nameStyle
      ) <~ contentStyle
    ) <~ rootStyle(heightCard)
  )

}

class ViewHolderCollectionAdapter(adapter: CollectionLayoutAdapter)(implicit context: ActivityContextWrapper, theme: NineCardsTheme)
  extends RecyclerView.ViewHolder(adapter.content)
  with CollectionAdapterStyles {

  val content = adapter.content

  val iconContent = adapter.iconContent

  val icon = adapter.icon

  val name = adapter.name

  def bind(card: Card, position: Int)(implicit fragment: Fragment): Ui[_] =
    (icon <~ iconCardTransform(card)) ~
      (name <~ tvText(card.term)) ~
      (content <~ vTag(position.toString))

}