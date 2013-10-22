package hudson.model;


import hudson.Extension;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public class RunOnceProject extends Project<RunOnceProject, RunOnceBuild> implements TopLevelItem {

    public RunOnceProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override
    protected Class<RunOnceBuild> getBuildClass() {
        return RunOnceBuild.class;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    /**
     * Descriptor is instantiated as a field purely for backward compatibility.
     * Do not do this in your code. Put @Extension on your DescriptorImpl class instead.
     */
    @Restricted(NoExternalUse.class)
    @Extension(ordinal=1000)
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends AbstractProjectDescriptor {
        public String getDisplayName() {
            return "Merge Requiest Build Job";
        }

        public RunOnceProject newInstance(ItemGroup parent, String name) {
            return new RunOnceProject(parent,name);
        }
    }
}
