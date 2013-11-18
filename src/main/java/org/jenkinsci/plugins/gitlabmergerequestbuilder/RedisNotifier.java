package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.GitlabCause;
import hudson.model.RunOnceProject;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.test.AbstractTestResultAction;
import jenkins.model.Jenkins;
import org.apache.commons.codec.digest.DigestUtils;
import org.kohsuke.stapler.export.*;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class RedisNotifier extends Notifier {

    private static final String REDIS_KEY = "resque:gitlab:queue:build_result";
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;

    private static final Logger logger = Logger.getLogger(RedisNotifier.class.getName());

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {

        GitlabCause cause = build.getCause(GitlabCause.class);
        if (cause == null) return true;

        logger.info("Serializing build result to JSON...");

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
        String consoleLog = Utils.escape(stringWriter.toString());

        String testResultJson = "null";
        AbstractTestResultAction testResult = build.getTestResultAction();
        if (testResult != null) {
            logger.info("Found test results. Serializing to JSON.");

            Model testResultModel = modelBuilder.get(testResult.getClass());
            w = new StringWriter();
            DataWriter testResultWriter = Flavor.JSON.createDataWriter(testResult, w);
            testResultModel.writeTo(testResult, new TreePruner.ByDepth(0), testResultWriter);
            testResultJson = w.toString();
        }

        FilePath coverageFile = build.getWorkspace().child("coverage/coverage.json");
        String coverageJson = "null";
        if (coverageFile.exists()) {
            logger.info("Found covegare info in [coverage/coverage.json]. Serializing to JSON.");
            coverageJson = coverageFile.readToString();
        } else {
            FilePath resultsetFile = build.getWorkspace().child("coverage/.resultset.json");
            if (resultsetFile.exists()) {
                logger.info("Found covegare info in [coverage/.resultset.json]. Serializing to JSON.");
                coverageJson = resultsetFile.readToString();
            }
        }

        RunOnceProject.DescriptorImpl descriptor = Jenkins.getInstance().
                getDescriptorByType(RunOnceProject.DescriptorImpl.class);


        String redisHost = descriptor.getRedisHost();
        if (redisHost == null || redisHost == "") redisHost = DEFAULT_REDIS_HOST;
        int redisPort = descriptor.getRedisPort();
        if (redisPort == 0) redisPort = DEFAULT_REDIS_PORT;
        logger.info("RPushing to key [" + REDIS_KEY + "] with Redis" +
                " on [" + redisHost + ":" + redisPort + "]");

        String json = "{\"buildId\": " + cause.buildId + ", " +
                "\"md5\": \"" + cause.md5 + "\", " +
                "\"buildResult\": " + buildResult + ", " +
                "\"testResult\": " + testResultJson + ", " +
                "\"coverage\": " + coverageJson + ", " +
                "\"consoleLog\": \"" + consoleLog + "\"}";

        // to comply Sidekiq format
        String value = "{\"class\":\"CiBuildResultWorker\",\"args\":[" + json + "]}";

        Jedis jedis = new Jedis(redisHost, redisPort);
        jedis.rpush(REDIS_KEY, value);

        return true;
    }

}
