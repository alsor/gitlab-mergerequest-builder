package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.RootAction;
import hudson.model.RunOnceProject;
import hudson.model.AbstractBuild;
import hudson.plugins.git.*;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import redis.clients.jedis.Jedis;

@Extension
public class BuildMergeRequestAction implements RootAction {

    private static final Logger logger = Logger.getLogger(BuildMergeRequestAction.class.getName());
    private static final String TARGET_REPO = "targetRepo";
    private static final String SOURCE_REPO = "sourceRepo";

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "/merge-request";
    }


    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException {

        JSONObject json = JSONObject.fromObject(slurp(req));

        String buildId = json.getString("build_id");
        String targetSha = json.getString("target_sha");
        String sourceSha = json.getString("source_sha");
        String targetUri = json.getString("target_uri");
        String sourceUri = json.getString("source_uri");

        String name = "p2";
        RunOnceProject project = (RunOnceProject) Jenkins.getInstance().createProject(RunOnceProject.DESCRIPTOR, name);

        List<UserRemoteConfig> userRemoteConfigs = new ArrayList<UserRemoteConfig>();
        userRemoteConfigs.add(new UserRemoteConfig(targetUri, TARGET_REPO, null));
        userRemoteConfigs.add(new UserRemoteConfig(sourceUri, SOURCE_REPO, null));

        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec(SOURCE_REPO + "/" + sourceSha));

        UserMergeOptions userMergeOptions = new UserMergeOptions(TARGET_REPO, targetSha);

        GitSCM scm = new GitSCM(null, userRemoteConfigs, branches,
                userMergeOptions,
                false, Collections.<SubmoduleConfig>emptyList(), false,
                false, new DefaultBuildChooser(), null, null, false, null,
                null,
                null, null, null, false, false, false, false, null, null, false, null, false, false);


        project.setScm(scm);

                project.getPublishersList().add(new Notifier() {
                    public BuildStepMonitor getRequiredMonitorService() {
                        return BuildStepMonitor.NONE;
                    }

                    @Override
                    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

                        Jedis jedis = new Jedis("localhost");

                        jedis.set("jenkins.merge-request-builder.result", build.getResult().toString());

                        return true;
                    }
                });

        project.scheduleBuild(null);

        rsp.getWriter().println("merge request id: " + json.get("mergeRequestId"));
    }

    private String slurp(StaplerRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder builder = new StringBuilder();
        String aux;

        while ((aux = reader.readLine()) != null) {
            builder.append(aux);
        }

        return builder.toString();
    }
}
