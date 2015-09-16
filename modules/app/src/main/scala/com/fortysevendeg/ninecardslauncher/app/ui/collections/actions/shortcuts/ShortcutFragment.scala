package com.fortysevendeg.ninecardslauncher.app.ui.collections.actions.shortcuts

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.View
import com.fortysevendeg.ninecardslauncher.app.di.Injector
import com.fortysevendeg.ninecardslauncher.app.ui.commons.{FragmentUiContext, UiContext, NineCardIntentConversions}
import com.fortysevendeg.ninecardslauncher.app.ui.commons.TasksOps._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.actions.BaseActionFragment
import com.fortysevendeg.ninecardslauncher.process.device.models.Shortcut
import com.fortysevendeg.ninecardslauncher2.R
import macroid.FullDsl._
import com.fortysevendeg.ninecardslauncher.app.ui.commons.ActivityResult._

import scalaz.concurrent.Task

class ShortcutFragment
  extends BaseActionFragment
  with ShortcutComposer
  with NineCardIntentConversions {

  implicit lazy val di: Injector = new Injector

  override def getLayoutId: Int = R.layout.list_action_fragment

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    runUi(initUi)
    Task.fork(di.deviceProcess.getAvailableShortcuts.run).resolveAsyncUi(
      onResult = (shortcut: Seq[Shortcut]) => addShortcuts(shortcut, shortcut => {
        runUi(unreveal())
        getActivity.startActivityForResult(shortcut.intent, shortcutAdded)
      })
    )
  }
}

