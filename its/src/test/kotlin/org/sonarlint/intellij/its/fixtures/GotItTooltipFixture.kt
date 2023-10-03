package org.sonarlint.intellij.its.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step

fun RemoteRobot.closeAllGotItTooltips() = step("Close all Got It Tooltips") {
    findAll(ComponentFixture::class.java, GotItTooltipFixture.firstButton()).forEach { it.click() }
}

@FixtureName("Got It Tooltip")
open class GotItTooltipFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    companion object {
        fun firstButton() = byXpath("//div[@class='JButton'][@text='Got It']")
    }

}
