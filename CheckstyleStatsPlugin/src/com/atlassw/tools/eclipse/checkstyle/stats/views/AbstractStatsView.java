//============================================================================
//
// Copyright (C) 2002-2005  David Schneider, Lars K�dderitzsch, Fabrice Bellingard
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//============================================================================
package com.atlassw.tools.eclipse.checkstyle.stats.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;
import org.eclipse.ui.progress.WorkbenchJob;

import com.atlassw.tools.eclipse.checkstyle.builder.CheckstyleMarker;
import com.atlassw.tools.eclipse.checkstyle.stats.StatsCheckstylePlugin;
import com.atlassw.tools.eclipse.checkstyle.stats.data.CreateStatsJob;
import com.atlassw.tools.eclipse.checkstyle.stats.data.MarkerStat;
import com.atlassw.tools.eclipse.checkstyle.stats.data.Stats;
import com.atlassw.tools.eclipse.checkstyle.stats.views.internal.CheckstyleMarkerFilter;
import com.atlassw.tools.eclipse.checkstyle.stats.views.internal.CheckstyleMarkerFilterDialog;

/**
 * Abstract view that gathers common behaviour for the stats views.
 * 
 * @author Fabrice BELLINGARD
 */
public abstract class AbstractStatsView extends ViewPart
{
    // TODO abstract further to allow non tableview based subclasses (f.i.
    // GraphStatsView)

    /**
     * Label de description.
     */
    private Label mDescLabel;

    /**
     * Viewer.
     */
    private TableViewer mViewer;

    //
    // attributes
    //

    /** The filter for this stats view. */
    private CheckstyleMarkerFilter mFilter;

    /** The focused resources. */
    private IResource[] mFocusedResources;

    /** The views private set of statistics. */
    private Stats mStats;

    /** The listener reacting to selection changes in the workspace. */
    private ISelectionListener mFocusListener;

    /** The listener reacting on resource changes. */
    private IResourceChangeListener mResourceListener;

    //
    // methods
    //

    /**
     * See method below.
     * 
     * @see org.eclipse.ui.IWorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
     */
    public void createPartControl(Composite parent)
    {
        // layout du p�re
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        parent.setLayout(layout);

        // la label
        mDescLabel = new Label(parent, SWT.NONE);
        GridData gridData = new GridData();
        gridData.horizontalAlignment = GridData.FILL;
        gridData.grabExcessHorizontalSpace = true;
        mDescLabel.setLayoutData(gridData);

        // le tableau
        mViewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL
            | SWT.V_SCROLL | SWT.SINGLE);
        gridData = new GridData(GridData.FILL_BOTH);
        mViewer.getControl().setLayoutData(gridData);

        // on cr�e les colonnes
        Table table = mViewer.getTable();
        table.setLinesVisible(true);
        table.setHeaderVisible(true);

        createColumns(table);

        // Les providers
        mViewer.setContentProvider(new ArrayContentProvider());
        mViewer.setLabelProvider(createLabelProvider());

        // create and register the workspace focus listener
        mFocusListener = new ISelectionListener()
        {
            public void selectionChanged(IWorkbenchPart part,
                ISelection selection)
            {
                AbstractStatsView.this.focusSelectionChanged(part, selection);
            }
        };

        getSite().getPage().addSelectionListener(mFocusListener);
        focusSelectionChanged(getSite().getPage().getActivePart(), getSite()
            .getPage().getSelection());

        // create and register the listener for resource changes
        mResourceListener = new IResourceChangeListener()
        {
            public void resourceChanged(IResourceChangeEvent event)
            {

                IMarkerDelta[] markerDeltas = event.findMarkerDeltas(
                    CheckstyleMarker.MARKER_ID, true);

                if (markerDeltas.length > 0)
                {
                    refresh();
                }
            }
        };

        ResourcesPlugin.getWorkspace().addResourceChangeListener(
            mResourceListener);

        makeActions();
    }

    /**
     * @see org.eclipse.ui.IWorkbenchPart#dispose()
     */
    public void dispose()
    {

        // IMPORTANT: Deregister listeners
        getSite().getPage().removeSelectionListener(mFocusListener);
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(
            mResourceListener);

        super.dispose();
    }

    /**
     * Adds the actions to the tableviewer context menu.
     * 
     * @param actions
     *            a collection of IAction objets
     */
    protected final void hookContextMenu(final Collection actions)
    {
        MenuManager menuMgr = new MenuManager();
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener()
        {
            public void menuAboutToShow(IMenuManager manager)
            {
                for (Iterator iter = actions.iterator(); iter.hasNext();)
                {
                    manager.add((IAction) iter.next());
                }
                manager.add(new Separator(
                    IWorkbenchActionConstants.MB_ADDITIONS));
            }
        });
        Menu menu = menuMgr.createContextMenu(mViewer.getControl());
        mViewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuMgr, mViewer);
    }

    /**
     * Specifies which action will be run when double clicking on the viewer.
     * 
     * @param action
     *            the IAction to add
     */
    protected final void hookDoubleClickAction(final IAction action)
    {
        mViewer.addDoubleClickListener(new IDoubleClickListener()
        {
            public void doubleClick(DoubleClickEvent event)
            {
                action.run();
            }
        });
    }

    /**
     * Opens the filters dialog for the specific stats view.
     */
    public final void openFiltersDialog()
    {

        CheckstyleMarkerFilterDialog dialog = new CheckstyleMarkerFilterDialog(
            getViewer().getControl().getShell(),
            (CheckstyleMarkerFilter) getFilter().clone());

        if (dialog.open() == Window.OK)
        {
            CheckstyleMarkerFilter filter = dialog.getFilter();
            filter.saveState(getDialogSettings());

            mFilter = filter;
            refresh();
        }
    }

    /**
     * Cf. m�thode surcharg�e.
     * 
     * @see org.eclipse.ui.IWorkbenchPart#setFocus()
     */
    public void setFocus()
    {
        mViewer.getControl().setFocus();
    }

    /**
     * Returns the table viewer.
     * 
     * @return Returns the mViewer.
     */
    public TableViewer getViewer()
    {
        return mViewer;
    }

    /**
     * Returns the main label of the view.
     * 
     * @return Returns the mDescLabel.
     */
    public Label getDescLabel()
    {
        return mDescLabel;
    }

    /**
     * Returns the filter of this view.
     * 
     * @return the filter
     */
    protected final CheckstyleMarkerFilter getFilter()
    {
        if (mFilter == null)
        {
            mFilter = new CheckstyleMarkerFilter();
            mFilter.restoreState(getDialogSettings());
        }

        return mFilter;
    }

    /**
     * Returns the statistics data.
     * 
     * @return the data of this view
     */
    protected final Stats getStats()
    {
        return mStats;
    }

    /**
     * Causes the view to re-sync its contents with the workspace. Note that
     * changes will be scheduled in a background job, and may not take effect
     * immediately.
     */
    protected final void refresh()
    {

        IWorkbenchSiteProgressService service = (IWorkbenchSiteProgressService) getSite()
            .getAdapter(IWorkbenchSiteProgressService.class);

        // rebuild statistics data
        CreateStatsJob job = new CreateStatsJob(getFilter());
        job.setRule(ResourcesPlugin.getWorkspace().getRoot());
        job.addJobChangeListener(new JobChangeAdapter()
        {
            public void done(IJobChangeEvent event)
            {
                mStats = ((CreateStatsJob) event.getJob()).getStats();
                Job uiJob = new WorkbenchJob("Refresh Checkstyle statistics")
                {

                    public IStatus runInUIThread(IProgressMonitor monitor)
                    {
                        handleStatsRebuilt();
                        return Status.OK_STATUS;
                    }
                };
                uiJob.setPriority(Job.INTERACTIVE);
                uiJob.setSystem(true);
                uiJob.schedule();
            }
        });

        service.schedule(job, 0, true);
    }

    /**
     * Returns the dialog settings for this view.
     * 
     * @return the dialog settings
     */
    protected final IDialogSettings getDialogSettings()
    {

        String concreteViewId = getViewId();

        IDialogSettings workbenchSettings = StatsCheckstylePlugin.getDefault()
            .getDialogSettings();
        IDialogSettings settings = workbenchSettings.getSection(concreteViewId);

        if (settings == null)
        {
            settings = workbenchSettings.addNewSection(concreteViewId);
        }

        return settings;
    }

    /**
     * Returns the view id of the concrete view. This is used to make separate
     * filter settings (stored in dialog settings) for different concrete views
     * possible.
     * 
     * @return the view id
     */
    protected abstract String getViewId();

    /**
     * Callback for subclasses to refresh the content of their controls, since
     * the statistics data has been updated.<br/>Note that the subclass should
     * check if their controls have been disposed, since this method is called
     * by a job that might run even if the view has been closed.
     */
    protected abstract void handleStatsRebuilt();

    /**
     * Returns an appropriate LabelProvider for the elements being displayed in
     * the table viewer.
     * 
     * @return a label provider
     */
    protected abstract IBaseLabelProvider createLabelProvider();

    /**
     * Cr�e les colonnes du tableau.
     * 
     * @param table :
     *            le tableau
     */
    protected abstract void createColumns(Table table);

    /**
     * Create the wiewer actions. hookContextMenu and hookDoubleClickAction can
     * be called inside this method.
     * 
     * @see AbstractStatsView#hookContextMenu(Collection)
     * @see AbstractStatsView#hookDoubleClickAction(IAction)
     */
    protected abstract void makeActions();

    /**
     * Invoked on selection changes within the workspace.
     * 
     * @param part
     *            the workbench part the selection occurred
     * @param selection
     *            the selection
     */
    private void focusSelectionChanged(IWorkbenchPart part, ISelection selection)
    {

        List resources = new ArrayList();
        if (part instanceof IEditorPart)
        {
            IEditorPart editor = (IEditorPart) part;
            IFile file = ResourceUtil.getFile(editor.getEditorInput());
            if (file != null)
            {
                resources.add(file);
            }
        }
        else
        {
            if (selection instanceof IStructuredSelection)
            {
                for (Iterator iterator = ((IStructuredSelection) selection)
                    .iterator(); iterator.hasNext();)
                {
                    Object object = iterator.next();
                    if (object instanceof IAdaptable)
                    {
                        IResource resource = (IResource) ((IAdaptable) object)
                            .getAdapter(IResource.class);

                        if (resource == null)
                        {
                            resource = (IResource) ((IAdaptable) object)
                                .getAdapter(IFile.class);
                        }

                        if (resource != null)
                        {
                            resources.add(resource);
                        }
                    }
                }
            }
        }

        IResource[] focusedResources = new IResource[resources.size()];
        resources.toArray(focusedResources);

        // check if update necessary -> if so then update
        boolean updateNeeded = updateNeeded(mFocusedResources, focusedResources);
        if (updateNeeded)
        {
            mFocusedResources = focusedResources;
            getFilter().setFocusResource(focusedResources);
            refresh();
        }
    }

    /**
     * Checks if an update of the statistics data is needed, based on the
     * current and previously selected resources. The current filter setting is
     * also taken into consideration.
     * 
     * @param oldResources
     *            the previously selected resources.
     * @param newResources
     *            the currently selected resources
     * @return <code>true</code> if an update of the statistics data is needed
     */
    private boolean updateNeeded(IResource[] oldResources,
        IResource[] newResources)
    {
        // determine if an update if refiltering is required
        CheckstyleMarkerFilter filter = getFilter();
        if (!filter.isEnabled())
        {
            return false;
        }

        int onResource = filter.getOnResource();
        if (onResource == CheckstyleMarkerFilter.ON_ANY_RESOURCE
            || onResource == CheckstyleMarkerFilter.ON_WORKING_SET)
        {
            return false;
        }
        if (newResources == null || newResources.length < 1)
        {
            return false;
        }
        if (oldResources == null || oldResources.length < 1)
        {
            return true;
        }
        if (Arrays.equals(oldResources, newResources))
        {
            return false;
        }
        if (onResource == CheckstyleMarkerFilter.ON_ANY_RESOURCE_OF_SAME_PROJECT)
        {
            Collection oldProjects = CheckstyleMarkerFilter
                .getProjectsAsCollection(oldResources);
            Collection newProjects = CheckstyleMarkerFilter
                .getProjectsAsCollection(newResources);

            if (oldProjects.size() == newProjects.size())
            {
                return !newProjects.containsAll(oldProjects);
            }
            else
            {
                return true;
            }
        }

        return true;
    }

    /**
     * Class used to listen to table viewer column header clicking to sort the
     * different kind of values.
     */
    protected class SorterSelectionListener extends SelectionAdapter
    {
        private ViewerSorter mSorter;

        private ViewerSorter mReverseSorter;

        private ViewerSorter mCurrentSorter;

        /**
         * Constructor.
         * 
         * @param sorter :
         *            the sorter to use
         */
        public SorterSelectionListener(ViewerSorter sorter)
        {
            mSorter = sorter;
            mReverseSorter = ((AbstractStatsSorter) sorter).getReverseSorter();
        }

        public void widgetSelected(SelectionEvent e)
        {
            if (mCurrentSorter == mReverseSorter)
            {
                mCurrentSorter = mSorter;
            }
            else
            {
                mCurrentSorter = mReverseSorter;
            }
            mViewer.setSorter(mCurrentSorter);
        }
    }

    /**
     * Abstract reverse sorter.
     */
    protected abstract class AbstractStatsSorter extends ViewerSorter implements
        Cloneable
    {
        /**
         * pour faire un tri dans l'autre sens.
         */
        private boolean mReverse;

        /**
         * constructeur.
         */
        public AbstractStatsSorter()
        {
            this.mReverse = false;
        }

        /**
         * @return Returns the mReverse.
         */
        public boolean isReverse()
        {
            return mReverse;
        }

        /**
         * Returns the reverse sorter.
         * 
         * @return the reverse sorter, or null if a problem occured
         */
        public AbstractStatsSorter getReverseSorter()
        {
            AbstractStatsSorter sorter = null;
            try
            {
                sorter = (AbstractStatsSorter) this.clone();
                sorter.mReverse = true;
            }
            catch (CloneNotSupportedException e)
            {
                // shouldn't happen. If so, let's return null
            }
            return sorter;
        }

        /**
         * See method below.
         * 
         * @see org.eclipse.jface.viewers.ViewerSorter#compare(org.eclipse.jface.viewers.Viewer,
         *      java.lang.Object, java.lang.Object)
         */
        public abstract int compare(Viewer viewer, Object e1, Object e2);
    }

    /**
     * Sorter for Strings.
     */
    protected class NameSorter extends AbstractStatsSorter
    {
        /**
         * See method below.
         * 
         * @see org.eclipse.jface.viewers.ViewerSorter#compare(org.eclipse.jface.viewers.Viewer,
         *      java.lang.Object, java.lang.Object)
         */
        public int compare(Viewer viewer, Object e1, Object e2)
        {
            ITableLabelProvider provider = (ITableLabelProvider) ((TableViewer) viewer)
                .getLabelProvider();

            String label1 = provider.getColumnText(e1, 0);
            String label2 = provider.getColumnText(e2, 0);

            return (isReverse()) ? label1.compareTo(label2) : label2
                .compareTo(label1);
        }
    }

    /**
     * Sorter for Integers.
     */
    protected class CountSorter extends AbstractStatsSorter
    {
        /**
         * See method below.
         * 
         * @see org.eclipse.jface.viewers.ViewerSorter#compare(org.eclipse.jface.viewers.Viewer,
         *      java.lang.Object, java.lang.Object)
         */
        public int compare(Viewer viewer, Object e1, Object e2)
        {
            int count1 = ((MarkerStat) e1).getCount();
            int count2 = ((MarkerStat) e2).getCount();

            return (isReverse()) ? count1 - count2 : count2 - count1;
        }
    }

}