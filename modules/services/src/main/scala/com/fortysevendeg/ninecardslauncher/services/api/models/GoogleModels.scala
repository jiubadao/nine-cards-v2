package com.fortysevendeg.ninecardslauncher.services.api.models

case class GooglePlayPackage(app: GooglePlayApp)

case class GoogleDevice(
  name: String,
  deviceId: String,
  secretToken: String,
  permissions: Seq[String])

case class GooglePlayApp(
  docid: String,
  title: String,
  creator: String,
  descriptionHtml: Option[String],
  icon: Option[String],
  background: Option[String],
  screenshots: Seq[String],
  video: Option[String],
  details: GooglePlayDetails,
  offer: Seq[GooglePlayOffer],
  aggregateRating: GooglePlayAggregateRating)

case class GooglePlayImage(
  imageType: Int,
  imageUrl: String,
  creator: Option[String])

case class GooglePlayDetails(
  appDetails: GooglePlayAppDetails)

case class GooglePlayAppDetails(
  appCategory: Seq[String],
  numDownloads: String,
  developerEmail: Option[String],
  developerName: Option[String],
  developerWebsite: Option[String],
  versionCode: Option[Int],
  versionString: Option[String],
  appType: Option[String],
  permission: Seq[String])

case class GooglePlayOffer(
  formattedAmount: String,
  micros: Long)

case class GooglePlayAggregateRating(
  ratingsCount: Int,
  commentCount: Option[Int],
  oneStarRatings: Int,
  twoStarRatings: Int,
  threeStarRatings: Int,
  fourStarRatings: Int,
  fiveStarRatings: Int,
  starRating: Double)

case class GooglePlaySimplePackages(
  errors: Seq[String],
  items: Seq[GooglePlaySimplePackage])

case class GooglePlaySimplePackage(
  packageName: String,
  appType: String,
  appCategory: String,
  numDownloads: String,
  starRating: Double,
  ratingCount: Int,
  commentCount: Int)

