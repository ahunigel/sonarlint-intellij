package org.sonarlint.intellij.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Nigel.Zheng on 4/9/2018.
 */
public class SonarAnalyzeModuleFilesAction extends AbstractSonarAction {
  private static final String HIDE_WARNING_PROPERTY = "SonarLint.analyzeModuleFiles.hideWarning";

  public SonarAnalyzeModuleFilesAction() {
  }

  public SonarAnalyzeModuleFilesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected boolean isEnabled(AnActionEvent e, Project project, SonarLintStatus status) {
    Module module = getCurrentModule(e);
    return module != null && !status.isRunning() && !getAllFiles(module).isEmpty();
  }

  @Nullable
  private Module getCurrentModule(AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) return null;
    return ModuleUtil.findModuleForFile(file, e.getProject());
  }

  @Override
  protected boolean isVisible(String place) {
    return !ActionPlaces.PROJECT_VIEW_POPUP.equals(place);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {

    Module module = getCurrentModule(e);

    if (module == null || ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace()) || !showModuleWarning(module)) {
      return;
    }

    SonarLintSubmitter submitter = SonarLintUtils.get(module, SonarLintSubmitter.class);
    Collection<VirtualFile> allFiles = getAllFiles(module);
    AnalysisCallback callback = new ShowModuleAnalysisResultsCallable(module, allFiles, "module files");
    submitter.submitFiles(allFiles, TriggerType.ALL, callback, false);
  }

  private static Collection<VirtualFile> getAllFiles(ComponentManager module) {
    List<VirtualFile> fileList = new ArrayList<>();
    ModuleFileIndex fileIndex = SonarLintUtils.get(module, ModuleRootManager.class).getFileIndex();
    fileIndex.iterateContent(vFile -> {
      if (!vFile.isDirectory() && !ProjectCoreUtil.isProjectOrWorkspaceFile(vFile, vFile.getFileType())) {
        fileList.add(vFile);
      }
      return true;
    });
    return fileList;
  }

  static boolean showModuleWarning(Module module) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !PropertiesComponent.getInstance().getBoolean(HIDE_WARNING_PROPERTY, false)) {
      int result = Messages.showYesNoDialog("Analysing all files from module " + module.getName()
              + " may take a considerable amount of time to complete.\n"
              + "To get the best from SonarLint, you should preferably use the automatic analysis of the file you're working on.",
          "SonarLint - Analyze Module Files - " + module.getName(),
          "Proceed", "Cancel", Messages.getWarningIcon(), new DoNotShowAgain());
      return result == Messages.OK;
    }
    return true;
  }

  // Don't use DialogWrapper.DoNotAskOption.Adapter because it's not implemented in older versions of intellij
  static class DoNotShowAgain implements DialogWrapper.DoNotAskOption {
    @Override
    public boolean isToBeShown() {
      return true;
    }

    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
      PropertiesComponent.getInstance().setValue(HIDE_WARNING_PROPERTY, Boolean.toString(!toBeShown));
    }

    @Override
    public boolean canBeHidden() {
      return true;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return false;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      return "Don't show again";
    }
  }
}
