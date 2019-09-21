/*
 * Created on 02.01.2004
 */
package org.jl.nwn.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 */
public class NwnDirRepository extends AbstractRepository {

	private File dir;
	
	public NwnDirRepository( File dir ){
		this.dir = dir;
	}

	/* (non-Javadoc)
	 * @see org.jl.nwn.resource.NwnRepository#getResource(org.jl.nwn.resource.ResourceID)
	 */
	public InputStream getResource(ResourceID id) throws IOException {
		File f = findFile( id );
		return f!=null ? new FileInputStream(f) : null;
	}
	
	/**
	 * return the File containing the given resource. if no file with the exact name
	 * returned by id.toFileName() is found, the method will perform a case insensitive
	 * search for the file
	 * @return File object pointing to the resource identified by id or null if no such file is found
	 * */
	private File findFile( ResourceID id ){
		String fName = id.toFileName();
		File f = new File( dir, fName );
		return f.isFile() ? f : findFileIgnoreCase( fName );
	}
	
	private File findFileIgnoreCase( String fileName ){
		String[] fNames = dir.list();
		for ( int i = 0, n = fNames.length; i<n; i++ ){
			if ( fNames[i].equalsIgnoreCase( fileName ) ){
				File f = new File( dir, fNames[i] );
				return f.isFile() ? f : null;
			}
		}
		return null; 
	}

	/* (non-Javadoc)
	 * @see org.jl.nwn.resource.NwnRepository#getResourceLocation(org.jl.nwn.resource.ResourceID)
	 */
	public File getResourceLocation(ResourceID id) {
		return contains(id)? dir : null;//new File( dir, id.toFileName() );
	}

	/* (non-Javadoc)
	 * @see org.jl.nwn.resource.NwnRepository#contains(org.jl.nwn.resource.ResourceID)
	 */
	public boolean contains(ResourceID id) {
		return findFile( id ) != null;
	}
	
	public OutputStream putResource( ResourceID id ) throws IOException{
		return new FileOutputStream( new File( dir, id.toFileName() ) );
	}	
	
	public Set getResourceIDs(){
		TreeSet s = new TreeSet();
		File[] files = dir.listFiles();
		for ( int i = 0; i < files.length; i++ )
			if ( files[i].isFile() )
				s.add( ResourceID.forFile( files[i] ) );
		return Collections.unmodifiableSet( s );
	}

	public boolean isWritable() {
		return dir.canWrite();
	}

	public long lastModified(ResourceID id) {
		File f = findFile( id );
		return f!=null? f.lastModified() : 0;
	}
        
        public int getResourceSize( ResourceID id ){
            File f = findFile(id);
            return f != null ?
                (int)f.length():
                0;
        }

}
