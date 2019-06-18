package org.jazzcommunity.attributeValueProviders.restrictCategory;

import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.common.IWorkItemCommon;
import com.ibm.team.workitem.common.internal.attributeValueProviders.IConfiguration;
import com.ibm.team.workitem.common.internal.attributeValueProviders.IValueSetProvider2;
import com.ibm.team.workitem.common.model.IAttribute;
import com.ibm.team.workitem.common.model.ICategory;
import com.ibm.team.workitem.common.model.ICategoryHandle;
import com.ibm.team.workitem.common.model.IWorkItem;
import org.eclipse.core.runtime.IProgressMonitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RestrictCategoryValueProvider implements IValueSetProvider2 {

    @Override
    public List getValueSet(
            IValueSetProvider2.ValueSetProviderInput valueSetProviderInput, IProgressMonitor monitor)
            throws TeamRepositoryException {
        return getCategoryList(
                valueSetProviderInput.getAttribute(),
                valueSetProviderInput.getWorkItemCommon(),
                valueSetProviderInput.getConfiguration(),
                monitor);
    }

    @Override
    public List getValueSet(
            IAttribute attribute,
            IWorkItem workItem,
            IWorkItemCommon workItemCommon,
            IConfiguration configuration,
            IProgressMonitor monitor)
            throws TeamRepositoryException {
        return getCategoryList(attribute, workItemCommon, configuration, monitor);
    }

    private List<ICategory> getCategoryList(
            IAttribute attribute,
            IWorkItemCommon workItemCommon,
            IConfiguration configuration,
            IProgressMonitor monitor)
            throws TeamRepositoryException {
        IConfiguration include = configuration.getChild("include");
        IConfiguration exclude = configuration.getChild("exclude");

        String includePath = getPath(include);
        boolean includeChildOnly = getChildOnlyBool(include);
        String excludePath = getPath(exclude);
        boolean excludeChildOnly = getChildOnlyBool(exclude);

        if (includePath == null && excludePath == null) {
            throw new IllegalArgumentException(
                    "Restrict Category Value Provider: Unable to read config!");
        }
        // all available categories
        List<ICategory> categories =
                workItemCommon.findCategories(attribute.getProjectArea(), ICategory.SMALL_PROFILE, monitor);
        // the 'Unassigned' category
        ICategory unassignedCategory = workItemCommon.findUnassignedCategory(attribute.getProjectArea(), ICategory.SMALL_PROFILE, monitor);

        ICategory includeCategory =
                resolveCategoryByNamePath(includePath, workItemCommon, attribute, categories, monitor);
        ICategory excludeCategory =
                resolveCategoryByNamePath(excludePath, workItemCommon, attribute, categories, monitor);

        if (includePath != null) {
            categories = includeCategoriesBy(includeCategory, categories, includeChildOnly);
        }
        if (excludePath != null) {
            categories = excludeCategoriesBy(excludeCategory, categories, excludeChildOnly);
        }
        categories.add(unassignedCategory);

        return categories;
    }

    private List<ICategory> includeCategoriesBy(
            ICategory includeCategory, List<ICategory> categories, boolean includeChildOnly) {
        if(includeCategory == null) {
            return new ArrayList<>();
        }
        return filterCategoriesBy(includeCategory, categories, includeChildOnly, false);
    }

    private List<ICategory> excludeCategoriesBy(
            ICategory excludeCategory, List<ICategory> categories, boolean excludeChildOnly) {
        if(excludeCategory == null) {
            return categories;
        }
        return filterCategoriesBy(excludeCategory, categories, excludeChildOnly, true);
    }

    private List<ICategory> filterCategoriesBy(
            ICategory category, List<ICategory> categories, boolean childOnly, boolean reverseFilter) {
        // only include categories that contain the 'root' category path in their path
        List<ICategory> subCategories =
                categories
                        .stream()
                        .filter(x -> (reverseFilter != category.getCategoryId().contains(x.getCategoryId())))
                        .filter(x -> !(childOnly && category.getCategoryId().equals(x.getCategoryId())))
                        .collect(Collectors.toList());
        return subCategories;
    }

    private ICategory resolveCategoryByNamePath(
            String path,
            IWorkItemCommon workItemCommon,
            IAttribute attribute,
            List<ICategory> categories,
            IProgressMonitor monitor)
            throws TeamRepositoryException {
        if (path == null) return null;

        // passed in 'root' category to filter for
        ICategoryHandle categoryHandle =
                workItemCommon.findCategoryByNamePath(
                        attribute.getProjectArea(), Arrays.asList(path.split("/")), monitor);
        // the resolved 'root' category
        ICategory category =
                categories
                        .stream()
                        .filter(x -> x.getItemHandle().sameItemId(categoryHandle))
                        .findFirst()
                        .orElse(null);
        return category;
    }

    private boolean getChildOnlyBool(IConfiguration config) {
        if (config == null) return false;
        return Boolean.parseBoolean(config.getString("childOnly"));
    }

    private String getPath(IConfiguration config) {
        if (config == null) return null;
        String path = config.getString("categoryPath");
        if (path == null || "".equals(path.trim())) return null;
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
