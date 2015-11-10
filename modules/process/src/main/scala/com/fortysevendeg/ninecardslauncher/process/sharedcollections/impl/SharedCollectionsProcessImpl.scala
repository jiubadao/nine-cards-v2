package com.fortysevendeg.ninecardslauncher.process.sharedcollections.impl

import com.fortysevendeg.ninecardslauncher.commons.contexts.ContextSupport
import com.fortysevendeg.ninecardslauncher.process.sharedcollections._
import com.fortysevendeg.ninecardslauncher.process.utils.ApiUtils
import com.fortysevendeg.ninecardslauncher.services.api.ApiServices
import com.fortysevendeg.ninecardslauncher.services.persistence.PersistenceServices
import com.fortysevendeg.ninecardslauncher.commons.NineCardExtensions.ResultTExtensions

class SharedCollectionsProcessImpl(apiServices: ApiServices, persistenceServices: PersistenceServices)
  extends SharedCollectionsProcess
  with Conversions
  with ImplicitsSharedCollectionsExceptions {

  val apiUtils = new ApiUtils(persistenceServices)

  override def getSharedCollectionsByCategory(
    category: String,
    typeShareCollection: TypeSharedCollection,
    offset: Int = 0,
    limit: Int = 50)
    (implicit context: ContextSupport) =
    (for {
      userConfig <- apiUtils.getRequestConfig
      response <- apiServices.getShareCollectionsByCategory(category, typeShareCollection.name, offset, limit)(userConfig)
    } yield response.items map toSharedCollection).resolve[SharedCollectionsExceptions]

}
