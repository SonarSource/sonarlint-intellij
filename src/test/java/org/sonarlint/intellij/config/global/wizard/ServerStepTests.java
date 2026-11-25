/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.config.global.wizard;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.util.ui.UIUtil;
import java.util.List;
import javax.swing.JRadioButton;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

import static org.assertj.core.api.Assertions.assertThat;

class ServerStepTests extends AbstractSonarLintLightTests {

  private static final String EU_LABEL = "EU - sonarcloud.io";
  private static final String US_LABEL = "US - sonarqube.us";
  private static final String AU_LABEL = "AU - sonarqube.au";

  @Test
  void new_connection_defaults_to_eu() throws CommitStepException {
    var model = newModel();
    var step = newStep(model, false);

    assertSelectedRegionRadio(step, EU_LABEL);
    commit(step);

    assertThat(model.getRegion()).isEqualTo(SonarCloudRegion.EU);
    assertThat(model.getServerUrl()).isEqualTo("https://sonarcloud.io");
  }

  @Test
  void offers_one_radio_per_region_without_invite_only_label() {
    var step = newStep(newModel(), false);

    assertThat(regionRadios(step)).extracting(JRadioButton::getText).containsExactly(EU_LABEL, US_LABEL, AU_LABEL);
  }

  @Test
  void editing_selects_the_radio_of_the_connection_region() {
    for (var entry : List.of(
      new Object[] {SonarCloudRegion.EU, EU_LABEL},
      new Object[] {SonarCloudRegion.US, US_LABEL},
      new Object[] {SonarCloudRegion.AU, AU_LABEL})) {
      var step = newStep(newModel().setRegion((SonarCloudRegion) entry[0]), true);

      assertSelectedRegionRadio(step, (String) entry[1]);
    }
  }

  @Test
  void selecting_us_saves_us_region_and_url() throws CommitStepException {
    var model = newModel();
    var step = newStep(model, false);

    radio(step, US_LABEL).setSelected(true);
    commit(step);

    assertThat(model.getRegion()).isEqualTo(SonarCloudRegion.US);
    assertThat(model.getServerUrl()).isEqualTo("https://sonarqube.us");
  }

  @Test
  void selecting_au_saves_au_region_and_url() throws CommitStepException {
    var model = newModel();
    var step = newStep(model, false);

    radio(step, AU_LABEL).setSelected(true);
    commit(step);

    assertThat(model.getRegion()).isEqualTo(SonarCloudRegion.AU);
    assertThat(model.getServerUrl()).isEqualTo("https://sonarqube.au");
  }

  @Test
  void editing_an_au_connection_keeps_au_on_save() throws CommitStepException {
    var model = newModel().setRegion(SonarCloudRegion.AU);
    var step = newStep(model, true);

    commit(step);

    assertThat(model.getRegion()).isEqualTo(SonarCloudRegion.AU);
    assertThat(model.getServerUrl()).isEqualTo("https://sonarqube.au");
  }

  @Test
  void region_radios_are_disabled_when_sonarqube_server_is_selected() {
    var step = newStep(newModel(), false);
    assertThat(regionRadios(step)).allMatch(JRadioButton::isEnabled);

    // the product radios have no text: SonarQube Server is the one not selected by default
    UIUtil.findComponentsOfType(step.getComponent(), JRadioButton.class).stream()
      .filter(r -> r.getText().isEmpty() && !r.isSelected()).findFirst().orElseThrow().setSelected(true);

    assertThat(regionRadios(step)).noneMatch(JRadioButton::isEnabled);
  }

  private static ConnectionWizardModel newModel() {
    return new ConnectionWizardModel().setName("name");
  }

  private static ServerStep newStep(ConnectionWizardModel model, boolean editing) {
    return new ServerStep(model, editing, List.of());
  }

  private static void commit(ServerStep step) throws CommitStepException {
    step.commit(AbstractWizardStepEx.CommitType.Next);
  }

  private static List<JRadioButton> regionRadios(ServerStep step) {
    return UIUtil.findComponentsOfType(step.getComponent(), JRadioButton.class).stream()
      .filter(r -> r.getText() != null && r.getText().contains(" - sonar"))
      .toList();
  }

  private static JRadioButton radio(ServerStep step, String text) {
    return UIUtil.findComponentsOfType(step.getComponent(), JRadioButton.class).stream()
      .filter(r -> text.equals(r.getText())).findFirst().orElseThrow();
  }

  private static void assertSelectedRegionRadio(ServerStep step, String expectedText) {
    assertThat(regionRadios(step).stream().filter(JRadioButton::isSelected)).extracting(JRadioButton::getText)
      .containsExactly(expectedText);
  }
}
