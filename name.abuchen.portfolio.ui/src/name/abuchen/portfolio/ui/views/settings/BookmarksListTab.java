package name.abuchen.portfolio.ui.views.settings;

import javax.inject.Inject;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import name.abuchen.portfolio.model.Bookmark;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.ClientSettings;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.util.ContextMenu;
import name.abuchen.portfolio.ui.util.DesktopAPI;
import name.abuchen.portfolio.ui.util.viewers.Column;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport.ModificationListener;
import name.abuchen.portfolio.ui.util.viewers.ShowHideColumnHelper;
import name.abuchen.portfolio.ui.util.viewers.SimpleListContentProvider;
import name.abuchen.portfolio.ui.util.viewers.StringEditingSupport;

public class BookmarksListTab implements SettingsView.Tab, ModificationListener
{
    private static final String DEFAULT_URL = "http://example.net/{tickerSymbol}?isin={isin}&wkn={wkn}&name={name}"; //$NON-NLS-1$

    private TableViewer bookmarks;

    @Inject
    private Client client;

    @Inject
    private IPreferenceStore preferences;

    @Override
    public CTabItem createTab(CTabFolder folder)
    {
        Composite container = new Composite(folder, SWT.NONE);
        TableColumnLayout layout = new TableColumnLayout();
        container.setLayout(layout);

        bookmarks = new TableViewer(container, SWT.FULL_SELECTION | SWT.MULTI);

        ColumnEditingSupport.prepare(bookmarks);

        ShowHideColumnHelper support = new ShowHideColumnHelper(BookmarksListTab.class.getSimpleName() + "@bottom", //$NON-NLS-1$
                        preferences, bookmarks, layout);

        // Create Column for Bookmark
        Column column = new Column(Messages.BookmarksListView_bookmark, SWT.None, 150);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((Bookmark) element).getLabel();
            }

            @Override
            public Image getImage(Object element)
            {
                return Images.TEXT.image();
            }

        });

        new StringEditingSupport(Bookmark.class, "label").addListener(this).attachTo(column); //$NON-NLS-1$
        support.addColumn(column);

        // Create Column for URL
        column = new Column(Messages.BookmarksListView_url, SWT.None, 500);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((Bookmark) element).getPattern();
            }
        });

        new StringEditingSupport(Bookmark.class, "pattern").addListener(this).attachTo(column); //$NON-NLS-1$
        support.addColumn(column);

        support.createColumns();

        bookmarks.getTable().setHeaderVisible(true);
        bookmarks.getTable().setLinesVisible(true);

        bookmarks.setContentProvider(new SimpleListContentProvider());

        bookmarks.setInput(client.getSettings().getBookmarks());
        bookmarks.refresh();

        new ContextMenu(bookmarks.getTable(), m -> fillContextMenu(m)).hook();

        CTabItem item = new CTabItem(folder, SWT.NONE);
        item.setText(Messages.BookmarksListView_title);
        item.setControl(container);
        return item;
    }

    @Override
    public void onModified(Object element, Object newValue, Object oldValue)
    {
        client.markDirty();
    }

    private void fillContextMenu(IMenuManager manager)
    {
        Bookmark bookmark = (Bookmark) ((IStructuredSelection) bookmarks.getSelection()).getFirstElement();

        if (bookmark == null)
            return;

        if (!bookmark.isSeparator())
            addTestMenu(manager, bookmark);

        addMoveUpAndDownActions(manager, bookmark);

        manager.add(new Separator());
        manager.add(new Action(Messages.BookmarksListView_insertBefore)
        {
            @Override
            public void run()
            {
                Bookmark index = (Bookmark) ((IStructuredSelection) bookmarks.getSelection()).getFirstElement();
                Bookmark wl = new Bookmark(Messages.BookmarksListView_NewBookmark, DEFAULT_URL);

                client.getSettings().insertBookmark(index, wl);
                client.markDirty();

                bookmarks.setInput(client.getSettings().getBookmarks());
                bookmarks.editElement(wl, 0);
            }
        });

        manager.add(new Action(Messages.BookmarksListView_insertAfter)
        {
            @Override
            public void run()
            {
                Bookmark index = (Bookmark) ((IStructuredSelection) bookmarks.getSelection()).getFirstElement();
                Bookmark wl = new Bookmark(Messages.BookmarksListView_NewBookmark, DEFAULT_URL);

                client.getSettings().insertBookmarkAfter(index, wl);
                client.markDirty();

                bookmarks.setInput(client.getSettings().getBookmarks());
                bookmarks.editElement(wl, 0);
            }
        });

        manager.add(new Action(Messages.BookmarksListView_addSeparator)
        {
            @Override
            public void run()
            {
                Bookmark index = (Bookmark) ((IStructuredSelection) bookmarks.getSelection()).getFirstElement();
                Bookmark wl = new Bookmark("-", ""); //$NON-NLS-1$ //$NON-NLS-2$

                client.getSettings().insertBookmarkAfter(index, wl);
                client.markDirty();

                bookmarks.setInput(client.getSettings().getBookmarks());
            }
        });

        manager.add(new Separator());
        manager.add(new Action(Messages.BookmarksListView_delete)
        {
            @Override
            public void run()
            {
                ClientSettings settings = client.getSettings();
                for (Object element : ((IStructuredSelection) bookmarks.getSelection()).toArray())
                    settings.removeBookmark((Bookmark) element);

                client.markDirty();
                bookmarks.setInput(settings.getBookmarks());
            }
        });

    }

    private void addTestMenu(IMenuManager manager, Bookmark bookmark)
    {
        MenuManager securities = new MenuManager(Messages.MenuOpenSecurityOnSite);
        for (Security security : client.getSecurities())
        {
            securities.add(new Action(security.getName())
            {
                @Override
                public void run()
                {
                    DesktopAPI.browse(bookmark.constructURL(security));
                }
            });
        }
        manager.add(securities);
        manager.add(new Separator());
    }

    private void addMoveUpAndDownActions(IMenuManager manager, Bookmark bookmark)
    {
        int index = client.getSettings().getBookmarks().indexOf(bookmark);
        if (index > 0)
        {
            manager.add(new Action(Messages.MenuMoveUp)
            {
                @Override
                public void run()
                {
                    ClientSettings settings = client.getSettings();
                    settings.removeBookmark(bookmark);
                    settings.insertBookmark(index - 1, bookmark);
                    bookmarks.setInput(client.getSettings().getBookmarks());
                    client.markDirty();
                }
            });
        }

        if (index < client.getSettings().getBookmarks().size() - 1)
        {
            manager.add(new Action(Messages.MenuMoveDown)
            {
                @Override
                public void run()
                {
                    ClientSettings settings = client.getSettings();
                    settings.removeBookmark(bookmark);
                    settings.insertBookmark(index + 1, bookmark);
                    bookmarks.setInput(client.getSettings().getBookmarks());
                    client.markDirty();
                }
            });
        }
    }

    @Override
    public void showAddMenu(Shell shell)
    {
        Bookmark wl = new Bookmark(Messages.BookmarksListView_NewBookmark, DEFAULT_URL);

        client.getSettings().getBookmarks().add(wl);
        client.markDirty();

        bookmarks.setInput(client.getSettings().getBookmarks());
        bookmarks.editElement(wl, 0);
    }
}
