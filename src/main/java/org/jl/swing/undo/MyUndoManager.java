package org.jl.swing.undo;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.undo.UndoManager;

/**
 * added 2 Actions ( undo & redo ) that will be updated by this UndoManager
 * object ( enabled state & name )
 */
public class MyUndoManager extends UndoManager{
    
    protected Action undoAction = new AbstractAction(){
        @Override
        public void actionPerformed( ActionEvent e ){
            undo();
        }
    };
    
    protected Action redoAction = new AbstractAction(){
        @Override
        public void actionPerformed( ActionEvent e ){
            redo();
        }
    };
    
    protected void updateActions(){
        undoAction.setEnabled(canUndo());
        undoAction.putValue(Action.NAME, getUndoPresentationName());
        redoAction.setEnabled(canRedo());
        redoAction.putValue(Action.NAME, getRedoPresentationName());
    }
    
    /** Creates a new instance of MyUndoManager */
    public MyUndoManager() {
        super();
    }
    
    @Override
    public void undoableEditHappened(javax.swing.event.UndoableEditEvent e) {
        super.undoableEditHappened(e);
        updateActions();
    }
    
    @Override
    public void undoOrRedo() throws javax.swing.undo.CannotRedoException, javax.swing.undo.CannotUndoException {
        super.undoOrRedo();
        updateActions();
    }
    
    @Override
    public void undo() throws javax.swing.undo.CannotUndoException {
        super.undo();
        updateActions();
    }
    
    @Override
    public void redo() throws javax.swing.undo.CannotRedoException {
        super.redo();
        updateActions();
    }
    
    @Override
    public void discardAllEdits() {
        super.discardAllEdits();
        updateActions();
    }
    
    public Action getRedoAction(){
        return redoAction;
    }
    
    public Action getUndoAction(){
        return undoAction;
    }
    
}
