package cards.nine.models.types

sealed trait Category {
  def name: String
}

// Special categories for cards

case class AppCategory(nineCardCategory: NineCardsCategory) extends Category {
  override def name: String = nineCardCategory.name
}

case class MomentCategory(moment: NineCardsMoment) extends Category {
  override def name: String = moment.name
}

case object FreeCategory extends Category {
  override def name: String = Category.freeName
}

object Category {
  val freeName = "FREE"
}