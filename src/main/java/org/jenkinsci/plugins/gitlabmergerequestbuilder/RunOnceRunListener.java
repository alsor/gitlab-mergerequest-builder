package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.Extension;
import hudson.model.RunOnceBuild;
import hudson.model.listeners.RunListener;

import java.io.IOException;

@Extension
public class RunOnceRunListener extends RunListener<RunOnceBuild> {

    @Override
    public void onFinalized(RunOnceBuild runOnceBuild) {
        try {
            runOnceBuild.getProject().delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
