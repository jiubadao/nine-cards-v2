package cards.nine.app.ui.commons.ops

import cards.nine.models.types.NineCardCategory
import com.fortysevendeg.macroid.extras.ResourcesExtras._
import com.fortysevendeg.ninecardslauncher.R
import macroid.ContextWrapper

object NineCardsCategoryOps {

  implicit class NineCardCategoryOp(nineCardCategory: NineCardCategory) {

    def getIconCollectionWorkspace(implicit context: ContextWrapper): Int =
      resGetDrawableIdentifier(s"icon_collection_${nineCardCategory.getIconResource}") getOrElse R.drawable.icon_collection_default

    def getIconCollectionDetail(implicit context: ContextWrapper): Int =
      resGetDrawableIdentifier(s"icon_collection_${nineCardCategory.getIconResource}_detail") getOrElse R.drawable.icon_collection_default_detail

    def getName(implicit contextWrapper: ContextWrapper): String =
      resGetString(nineCardCategory.getStringResource) getOrElse nineCardCategory.name

  }

}