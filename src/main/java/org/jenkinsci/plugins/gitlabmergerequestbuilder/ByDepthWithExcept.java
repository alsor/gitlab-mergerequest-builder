package org.jenkinsci.plugins.gitlabmergerequestbuilder;

import org.kohsuke.stapler.export.Property;
import org.kohsuke.stapler.export.TreePruner;

import java.util.List;

// Turns out that the tree-traversing algorithm in the Model is dependent on the fact that the TreePruner
// object contains some kind of reduction rule to prevent infinite recursion. So we are using ByDepth code here
// and just adding capabilities to skip properties by name
public class ByDepthWithExcept extends TreePruner {

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
