package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import java.io.IOException;
import java.util.logging.Logger;

public class WorkspaceCleanup extends Notifier {

    private static final Logger logger = Logger.getLogger(WorkspaceCleanup.class.getName());

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        FilePath workspace = build.getWorkspace();
        logger.info("Deleting workspace [" + workspace.getRemote() + "] after merge request build");
        try {
            if (workspace != null && workspace.exists()) {
                workspace.deleteRecursive();
            }
        } catch (Exception ex) {
            logger.info("Error while deleting workspace: " + ex.getCause());
        }
        return true;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }
}
