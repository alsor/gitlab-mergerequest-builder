package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tasks.Notifier;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;

@Extension
public class BuildMergeRequestAction implements RootAction {
    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "/merge-request";
    }

    public HttpResponse doBuild() throws IOException {

        String name = "p2";
        RunOnceProject project = (RunOnceProject) Jenkins.getInstance().createProject(RunOnceProject.DESCRIPTOR, name);


        project.getPublishersList().add(new Notifier() {
            public BuildStepMonitor getRequiredMonitorService() {
                return BuildStepMonitor.NONE;
            }

            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().println("booom!");
                return true;
            }
        });

        project.scheduleBuild(null);

        return new HttpResponse() {
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                rsp.getWriter().println("abc");
            }
        };
    }
}
