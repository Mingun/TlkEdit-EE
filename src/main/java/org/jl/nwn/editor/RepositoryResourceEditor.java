/*
 * RepositoryResourceEditor.java
 *
 * Created on 31.08.2007, 11:26:37
 */

package org.jl.nwn.editor;

import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.JMenu;
import javax.swing.JToolBar;

import org.jl.nwn.Version;
import org.jl.nwn.resource.NwnRepository;
import org.jl.nwn.resource.ResourceID;

public class RepositoryResourceEditor extends SimpleFileEditorPanel {

    private final SimpleFileEditorPanel delegate;
    private final NwnRepository rep;
    private final ResourceID resID;
    private File savedAsFile = null;

    public RepositoryResourceEditor(SimpleFileEditorPanel delegate, NwnRepository rep, ResourceID resID) {
        this.delegate = delegate;
        this.rep = rep;
        this.resID = resID;
        setLayout(new BorderLayout());
        add(delegate, BorderLayout.CENTER);
    }

    /* (non-Javadoc)
     * @see org.jl.nwn.editor.SimpleFileEditorPanel#addChangeListener(javax.swing.event.ChangeListener)
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener cl) {
        delegate.addPropertyChangeListener(cl);
    }

    /**
     * @return
     */
    @Override
    public boolean canSave() {
        return savedAsFile == null ? rep.isWritable() : delegate.canSave();
    }

    /**
     * @return
     */
    @Override
    public boolean canSaveAs() {
        return delegate.canSaveAs();
    }

    /**
     *
     */
    @Override
    public void close() {
        delegate.close();
    }

    /**
     * @return
     */
    @Override
    public File getFile() {
        return savedAsFile == null ? new File(rep.getResourceLocation(resID) + "[" + resID.toFileName() + "]") : delegate.getFile();
    }

    /* (non-Javadoc)
     * @see org.jl.nwn.editor.SimpleFileEditorPanel#getIsModified()
     */
    @Override
    public boolean getIsModified() {
        return delegate.getIsModified();
    }

    /* (non-Javadoc)
     * @see org.jl.nwn.editor.SimpleFileEditorPanel#removeChangeListener(javax.swing.event.ChangeListener)
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener cl) {
        delegate.removePropertyChangeListener(cl);
    }

    /**
     * @throws IOException
     */
    @Override
    public void save() throws IOException {
        delegate.save();
        if (savedAsFile == null) {
            return;
        }
        File f = delegate.getFile();
        byte[] buffer = new byte[32000];
        InputStream is = null;
        OutputStream os = null;
        IOException e = null;
        try {
            is = new FileInputStream(f);
            os = rep.putResource(resID);
            int l = 0;
            while ((l = is.read(buffer)) != -1) {
                os.write(buffer, 0, l);
            }
            os.flush();
        } catch (IOException ioex) {
            e = ioex;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ioex) {
                if (e != null) {
                    e = ioex;
                }
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException ioex) {
                if (e != null) {
                    e = ioex;
                }
            }
            if (e != null) {
                throw e;
            }
        }
    }

    /**
     * @param f
     * @throws IOException
     */
    @Override
    public void saveAs(File f, Version v) throws IOException {
        delegate.saveAs(f, v);
        savedAsFile = f;
    }

    @Override
    public JMenu[] getMenus() {
        return delegate.getMenus();
    }

    @Override
    public JToolBar getToolbar() {
        return delegate.getToolbar();
    }

    @Override
    public void showToolbar(boolean b) {
        delegate.showToolbar(b);
    }
}
