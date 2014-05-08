package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import jenkins.model.DependencyDeclarer;

import java.io.IOException;

/*
 * Sole purpose of this class is to be able to programmatically
 * create upstream/downstream dependency beetwen two projects
 */
public class SetAsDownstreamOf extends Publisher implements DependencyDeclarer {

    private AbstractProject upstream;

    public SetAsDownstreamOf(AbstractProject project) {
        upstream = project;
    }

    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        graph.addDependency(new DependencyGraph.Dependency(upstream, owner));
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    public BuildStepMonitor getRequiredMonitorService() { return BuildStepMonitor.NONE; }
}
