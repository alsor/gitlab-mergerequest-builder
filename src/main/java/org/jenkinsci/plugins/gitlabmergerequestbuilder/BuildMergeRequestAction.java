package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.RootAction;
import hudson.model.RunOnceProject;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitStatus;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.scm.SCM;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.Notifier;
import hudson.tasks.Shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import hudson.tasks.test.AbstractTestResultAction;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.DataWriter;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.export.Model;
import org.kohsuke.stapler.export.ModelBuilder;
import org.kohsuke.stapler.export.Property;
import org.kohsuke.stapler.export.TreePruner;

import redis.clients.jedis.Jedis;

@Extension
public class BuildMergeRequestAction implements RootAction {

    private static final Logger logger = Logger.getLogger(BuildMergeRequestAction.class.getName());
    private static final String TARGET_REPO = "targetRepo";
    private static final String SOURCE_REPO = "sourceRepo";
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "/merge-request";
    }


    private static String createUniqueName(String buildId) {
        String base = "Build Merge Request #" + buildId;
        int suffix = 1;
        String name = base;
        while (Jenkins.getInstance().getItem(name) != null) {
            suffix++;
            name = base + "(" + suffix + ")";
        }
        return name;
    }

    public void doBuild(StaplerRequest req, StaplerResponse rsp) throws IOException, URISyntaxException {

        JSONObject json = JSONObject.fromObject(slurp(req));

        final String buildId = json.getString("build_id");
        final String targetSha = json.getString("target_sha");
        final String sourceSha = json.getString("source_sha");
        final String targetUri = json.getString("target_uri");
        final String sourceUri = json.getString("source_uri");

        String name = createUniqueName(buildId);

        final RunOnceProject.DescriptorImpl descriptor = Jenkins.getInstance().
                getDescriptorByType(RunOnceProject.DescriptorImpl.class);

        RunOnceProject project = (RunOnceProject) Jenkins.getInstance().createProject(descriptor, name);

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

        Project existingProject = findProjectByUri(new URIish(targetUri));
        if (existingProject != null) {
            project.setAssignedLabel(existingProject.getAssignedLabel());

            for (Object item : existingProject.getBuilders()) {
                 project.getBuildersList().add((Builder) item);
            }
        } else {
            String defaultCommands = "#!/bin/bash -i\n" +
                    "rvm use \"ruby-1.9.3\"\n" +
                    "ENV=test bundle install\n" +
                    "COVERAGE=true ENV=test bundle exec rake spec";
            project.getBuildersList().add(new Shell(defaultCommands));
        }


        project.getPublishersList().add(new Notifier() {
            public BuildStepMonitor getRequiredMonitorService() {
                return BuildStepMonitor.NONE;
            }

            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws InterruptedException, IOException {


                // we need to exclude any jenkins URLs from the build result.
                // the first problem is that project is deleted after the build so URLs will be stale anyway
                // and the second problem is that if Jenkins web location is not configured in the global settings
                // then we will get an exception trying to determine absolute URL
                List<String> excepts = new ArrayList<String>();
                excepts.add("absoluteUrl");
                excepts.add("url");
                TreePruner pruner = new ByDepthWithExcept(0, excepts);

                ModelBuilder modelBuilder = new ModelBuilder();
                StringWriter w = new StringWriter();
                Model buildModel = modelBuilder.get(build.getClass());
                DataWriter dataWriter = Flavor.JSON.createDataWriter(build, w);
                buildModel.writeTo(build, pruner, dataWriter);
                String buildResult = w.toString();

                StringWriter stringWriter = new StringWriter();
                build.getLogText().writeLogTo(0, stringWriter);
                String consoleLog = stringWriter.toString();

                String testResultJson = "null";
                AbstractTestResultAction testResult = build.getTestResultAction();
                if (testResult != null) {
                    Model testResultModel = modelBuilder.get(testResult.getClass());
                    w = new StringWriter();
                    DataWriter testResultWriter = Flavor.JSON.createDataWriter(testResult, w);
                    testResultModel.writeTo(testResult, new TreePruner.ByDepth(0), testResultWriter);
                    testResultJson = w.toString();
                }

                String redisHost = descriptor.getRedisHost();
                if (redisHost == null || redisHost == "") redisHost = DEFAULT_REDIS_HOST;
                int redisPort = descriptor.getRedisPort();
                if (redisPort == 0) redisPort = DEFAULT_REDIS_PORT;
                logger.info("using Redis host: " + redisHost);
                logger.info("using Redis port: " + redisPort);

                Jedis jedis = new Jedis(redisHost, redisPort);

                String md5 = DigestUtils.md5Hex(buildId + sourceUri + sourceSha + targetUri + targetSha);

                String json = "{\"buildId\": " + buildId + ", " +
                        "\"md5\": \"" + md5 + "\", " +
                        "\"buildResult\": " + buildResult + ", " +
                        "\"testResult\": " + testResultJson + ", " +
                        "\"consoleLog\": \"" + consoleLog + "\"}";
                jedis.rpush("resque:gitlab:queue:build_result", json);

                return true;
            }
        });

        project.scheduleBuild(null);

        rsp.getWriter().println("merge request id: " + json.get("mergeRequestId"));
    }

    // Turns out that the tree-traversing algorithm in the Model is dependent on the fact that the TreePruner
    // object contains some kind of reduction rule to prevent infinite recursion. So we are using ByDepth code here
    // and just adding capabilities to skip properties by name
    public static class ByDepthWithExcept extends TreePruner {
        final int n;
        final List<String> excepts;
        private ByDepthWithExcept next;

        public ByDepthWithExcept(int n, List<String> excepts) {
            this.n = n;
            this.excepts = excepts;
        }

        private ByDepthWithExcept next() {
            if (next==null)
                next = new ByDepthWithExcept(n+1, excepts);
            return next;
        }

        @Override
        public TreePruner accept(Object node, Property prop) {
            if (excepts.contains(prop.name)) return null;
            if (prop.visibility < n) return null;
            if (prop.inline)    return this;
            return next();
        }
    }

    private static Project findProjectByUri(URIish toFind) {
        for (Project project : Jenkins.getInstance().getAllItems(Project.class)) {
            SCM scm = project.getScm();
            if (scm instanceof GitSCM) {
                GitSCM gitSCM = (GitSCM) scm;
                for (RemoteConfig repository : gitSCM.getRepositories()) {
                    for (URIish existing : repository.getURIs()) {
                        if (GitStatus.looselyMatches(existing, toFind)) {
                            return project;
                        }
                    }

                }
            }
        }

        return null;
    }

    private static String slurp(StaplerRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder builder = new StringBuilder();
        String aux;

        while ((aux = reader.readLine()) != null) {
            builder.append(aux);
        }

        return builder.toString();
    }
}
