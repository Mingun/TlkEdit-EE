package org.jl.nwn.patcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jl.nwn.NwnLanguage;
import org.jl.nwn.Version;
import org.jl.nwn.erf.ErfFile;
import org.jl.nwn.gff.GffCExoLocString;
import org.jl.nwn.resource.NwnRepository;
import org.jl.nwn.resource.ResourceID;
import org.jl.nwn.tlk.DefaultTlkReader;
import org.jl.nwn.tlk.TlkContent;
import org.jl.nwn.tlk.TlkLookup;
import org.jl.nwn.tlk.TlkTool;
import org.jl.nwn.twoDa.TwoDaTable;

/* when joining patches :
  - DO NOT update absolute values
  - when computing offset for 2da files DO NOT count absolute lines ( marked with ! )
 */

public class Patcher {

	private static final String includedefFilename = "includedefs.txt";
	private static final String referencesFilename = "references.txt";
	private static final String tlkPatchFilename = "patch.tlk";
	private static final String tlkUpdateFilename = "diff.tlu";
	static final String lineSeparator = System.getProperty("line.separator");

	static class daReference {
        final int column;
        final String target;
		public daReference(String s, int col) {
			column = col;
			target = s;
		}
		@Override
		public String toString() {
			return "column " + column + " -> " + target;
		}
	}

    /** Return the output dir thats used when patch in patchDir is built. */
	public static File getOutputDir( File patchDir ){
		return new File( patchDir, "out" );
	}

    /** Return the tlk file thats written when patch in patchDir is built. */
	public static File getOutputTlk( File patchDir ){
		return new File( new File( getOutputDir( patchDir ), "tlk" ), "dialog.tlk" );
	}

    /** Return the hak file thats written when patch in patchDir is built. */
	public static File getOutputHak( File patchDir ){
		return new File( new File( getOutputDir( patchDir ), "hak" ), patchDir.getName() + ".hak" );
	}

	private static void compileScript( File script, File nwnhome, File outputdir ) throws IOException{
        String compilerExe;
		boolean use_clcompile = false;
		if (System
			.getProperty("os.name")
			.toLowerCase()
			.startsWith("win")){
				File clcompile_exe = new File( new File( nwnhome, "utils" ), "clcompile.exe" );
				compilerExe = clcompile_exe.getAbsolutePath();
				use_clcompile = true;
			}
		else compilerExe = "nwnnsscomp";

		String[] exec = use_clcompile ?
			new String[]{
				compilerExe,
				script.getAbsolutePath(),
				outputdir.getAbsolutePath()
			}
			: new String[]{ // use nwnnsscomp
				compilerExe,
				nwnhome.getAbsolutePath(),
				script.getAbsolutePath()
			};
		System.out.println( "exec : " + exec[0] + " " + exec[1] + " " + exec[2] );

		Process p = Runtime.getRuntime().exec( exec, null, outputdir );

		InputStream is = p.getInputStream();
        int len;
        final byte[] buf = new byte[32000];
        while ((len = is.read(buf)) != -1) {
            System.out.write(buf, 0, len);
        }
		is = p.getErrorStream();
        while ((len = is.read(buf)) != -1) {
            System.out.write(buf, 0, len);
        }
	}

    static void filemove(File src, File target) throws IOException {
        Files.move(src.toPath(), target.toPath(), REPLACE_EXISTING);
    }

    private static void filecopy(File src, File target) throws IOException {
        Files.copy(src.toPath(), target.toPath(), REPLACE_EXISTING);
    }

	private static void delRek(File f) {
		if (f.isDirectory()) {
			File[] files = f.listFiles();
            for (final File file : files) {
                delRek(file);
            }
			f.delete();
		} else if (f.isFile())
			f.delete();
	}

	static void catTextFiles(File[] input, File output, boolean append) throws IOException {
        try (final FileWriter out = new FileWriter(output, append)) {
            for (final File file : input) {
                if (!file.exists() || !file.isFile()) { continue; }

                try (final BufferedReader in = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        out.write(line);
                        out.write(lineSeparator);
                    }
                }
            }
        }
	}

	static void shiftReference(
		TwoDaTable t,
		int shift,
		int column,
		boolean updateAbsolute) {
		for (int i = 0; i < t.getRowCount(); i++) {
			String value = t.getValueAt(i, column);
			if (!value.startsWith("*")) {
				if (value.startsWith("!")) {
					// absolute value, remove leading '!', don't shift
					if (updateAbsolute)
						t.setValueAt(value.substring(1), i, column);
				} else
					t.setValueAt(
						Integer.toString(Integer.parseInt(value) + shift),
						i,
						column);
			}
		}
	}

    private static Map<String, daReference> readConstantDefs(File in) throws IOException {
        return readConstantDefs(new FileReader(in));
    }

    private static Map<String, daReference> readConstantDefs(InputStreamReader reader) throws IOException {
        try (final BufferedReader in = new BufferedReader(reader)) {
            final Map<String, daReference> cdefMap = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                final int p = line.indexOf(':');
                final int comma = line.indexOf(',');
                final String filename = line.substring(0, p).trim().toLowerCase();
                final String scriptname =
                    line.substring(p + 1, comma)
                        .trim()
                        .toLowerCase();
                final int column =
                    Integer.parseInt(
                        line.substring(comma + 1, line.indexOf(';'))
                            .trim());
                if (!cdefMap.containsKey(filename)) {
                    System.out.println(
                        "will write constant definitions for "
                            + filename
                            + " to "
                            + scriptname
                            + ", using column "
                            + column);
                    cdefMap.put(filename, new daReference(scriptname, column));
                }
            }
            return cdefMap;
        }
    }

    private static Map<String, daReference[]> readReferenceDefs(File in) throws IOException {
        return readReferenceDefs(new FileReader(in));
    }

    private static Map<String, daReference[]> readReferenceDefs(InputStreamReader reader) throws IOException {
        try (final BufferedReader in = new BufferedReader(reader)) {
            final Map<String, daReference[]> refMap = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                final int p = line.indexOf(':');
                final String filename = line.substring(0, p).trim().toLowerCase();
                if (refMap.containsKey(filename)) continue;

                System.out.println("read references for 2da file " + filename);
                final String[] r = line.substring(p + 1).split(";");
                final daReference[] refs = new daReference[r.length];
                for (int i = 0; i < refs.length; i++) {
                    final int comma = r[i].indexOf(',');
                    refs[i] = new daReference(
                        r[i].substring(comma + 1).toLowerCase().trim(),
                        Integer.parseInt(r[i].substring(0, comma).trim())
                    );
                }
                refMap.put(filename, refs);
            }
            return refMap;
        }
    }

    /**
     * @param updateAbsolute if {@code true}, lines with an absolute reference
     *        in the 1st column replace the existing line, others are appended
     */
    static void patch2da(TwoDaTable main, TwoDaTable patch, boolean updateAbsolute) {
        if (updateAbsolute) {
            for (int i = 0; i < patch.getRowCount(); i++) {
                final String first = patch.getValueAt(i, 0);
                if (first.startsWith("!")) {
                    final String val = first.substring(1);
                    final int line = Integer.parseInt(val);
                    main.setValueAt(val, line, 0);
                    for (int j = 1; j < patch.getColumnCount(); ++j) {
                        main.setValueAt(patch.getValueAt(i, j), line, j);
                    }
                    patch.removeRow(i);
                }
            }
        }
        main.append(patch, updateAbsolute);
    }

    /**
     * Write integer constant definitions for {@code table} to file {@code constScript},
     * use {@code column} for constant names, shift line number by {@code shift}
     */
	static void writeScriptConstants(
		TwoDaTable table,
		File constScript,
		int column,
		int shift,
		boolean append)
		throws IOException {
        try (final FileWriter out = new FileWriter(constScript, append)) {
            for (int j = 0; j < table.getRowCount(); j++) {
                if (table.getValueAt(j, 0).startsWith("!")) { continue; }

                final String val = table.getValueAt(j, column);
                // write no constant definition for replaced lines
                if (val.startsWith("*")) { continue; }

                out.write("const int " + val + " = " + (shift + j) + ";" + lineSeparator);
            }
        }
	}


	public static void applyPatch(
			File patchDir,
			NwnRepository sourceRep,
			File nwnDir,
			File tlkSourceFile,
			boolean compile,
			boolean buildHak ) throws IOException {
				 applyPatch( patchDir, sourceRep, nwnDir, tlkSourceFile, compile, buildHak, false );
			}


	public static void applyPatch(
		File patchDir,
		NwnRepository sourceRep,
		File nwnDir,
		File tlkSourceFile,
		boolean compile,
		boolean buildHak, boolean isUserTlk )
		throws IOException {
		File outputDir = new File(patchDir, "out");
		File scriptSrcDir = new File(patchDir, "scripts");
		outputDir.mkdirs();
		File tlkOutDir = new File(outputDir, "tlk");
		File tlkOutputFile = new File(tlkOutDir, "dialog.tlk");
		// remove files in output dir
        for (final File file : outputDir.listFiles()) {
            if (file.isFile()) {
                file.delete();
            }
        }
		// copy files from script dir to output dir
		if (scriptSrcDir.exists()) {
            for (final File file : scriptSrcDir.listFiles()) {
                if (file.isFile()) {
                    filecopy(file, new File(outputDir, file.getName()));
                }
            }
		}

        try (final PrintWriter patchinfo = new PrintWriter( new FileOutputStream( new File( outputDir, "patchinfo.txt")))) {
            // read reference file ...............
            final Map<String, daReference[]> refMap = readReferenceDefs(new File(patchDir, referencesFilename));
            final Map<String, daReference[]> defaultRefMap =
                readReferenceDefs(
                    new InputStreamReader(
                        ClassLoader.getSystemResourceAsStream(
                            "defaultreferences.txt")));
            // adding reference defaults to patch definitions ( overwriting patch definitions )
            refMap.putAll(defaultRefMap);

            // read constant definition file ...............
            final Map<String, daReference> cdefMap = readConstantDefs(new File(patchDir, includedefFilename));
            // read all 2da files from patch directory
            System.out.println("reading patch 2da files : ");
            File[] daPatchFiles = patchDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".2da");
                }
            });
            final TwoDaTable[] daPatchTables = new TwoDaTable[daPatchFiles.length];
            for (int i = 0; i < daPatchFiles.length; i++) {
                daPatchTables[i] = new TwoDaTable(daPatchFiles[i]);
                System.out.println(daPatchFiles[i].getName().toLowerCase());
            }
            //	note : daPatchFiles[i] <--> daPatchTables[i],
            //  i.e. daPatchFiles[i].getName() is the filename for daPatchTables[i]

            // read the 2da source files
            System.out.println("reading source 2da files : ");
            final TwoDaTable[] daSourceTables = new TwoDaTable[daPatchFiles.length];
            for (int i = 0; i < daSourceTables.length; i++) {
                final String patchName = daPatchFiles[i].getName();
                final String resName =
                    patchName
                        .substring(0, patchName.length() - 4)
                        .toLowerCase();
                final InputStream is = sourceRep.getResource(new ResourceID(resName, "2da"));
                if (is == null) {
                    System.out.println("couldn't find source file for " + resName + ".2da");
                } else {
                    System.out.println("loading source : " + resName);
                    daSourceTables[i] = new TwoDaTable(is);
                }
            }

            // build map for minimum sizes of 2da files ( filling lines )
            final Map<String, Integer> minsizes = new HashMap<>();
            try (final InputStream is = ClassLoader.getSystemResourceAsStream("min2dasizes.txt")) {
                final BufferedReader bin = new BufferedReader(new InputStreamReader(is));
                String aline;
                while ((aline = bin.readLine()) != null) {
                    System.out.print('.');
                    final int space = aline.indexOf(' ');
                    minsizes.put(
                        aline.substring(0, space).trim().toLowerCase(),
                        Integer.parseInt(aline.substring(space + 1).trim()));
                }
                System.out.println();
            }

            // create the offset map for all the files
            // the offset is simply the number of lines in the source 2da file
            // or the minsize entry for that file
            final Map<String, Integer> offsetMap = new TreeMap<>();
            for (int i = 0; i < daSourceTables.length; i++) {
                final String patchName = daPatchFiles[i].getName().toLowerCase();
                if (daSourceTables[i] != null) {
                    final Integer min = minsizes.getOrDefault(patchName, 0);
                    if (daSourceTables[i].getRowCount() >= min.intValue())
                        offsetMap.put(patchName, daSourceTables[i].getRowCount());
                    else
                        offsetMap.put(patchName, min);
                } else {
                    offsetMap.put(patchName, 0);
                }
            }

            // load and patch tlk file
            File tlkPatchFile = new File(patchDir, tlkPatchFilename);
            //File tlkSourceFile = new File(sourceRep, "dialog.tlk" );
            //File tlkSourceFile = new File(nwnDir, "dialog.tlk");
            int tlkOffset = 0;
            if (tlkPatchFile.exists()) {
                if (!tlkSourceFile.exists()) {
                    System.out.println("warning : no dialog.tlk found !");
                } else {
                    System.out.println("creating patched tlk file ...");
                    tlkOutDir.mkdirs();
                    TlkContent src =
                        new DefaultTlkReader(Version.getDefaultVersion())
                        .load( tlkSourceFile, null );
                    System.out.println("source tlk loaded");
                    File tlkDiff = new File( patchDir, tlkUpdateFilename );
                    if ( tlkDiff.exists() ){
                        if ( !isUserTlk ){
                            System.out.println("applying tlk diff ...");
                            src.mergeDiff( tlkDiff );
                            System.out.println("diff applied");
                        }
                        else
                            System.out.println( "warning : tlk diff exists but cannot be applied because tlk source is a user tlk file" );
                    }
                    tlkOffset = src.size();
                    System.out.println("appending patch.tlk ( at " + tlkOffset + " )");
                    TlkContent patch = new DefaultTlkReader(Version.getDefaultVersion()).load( tlkPatchFile, null );
                    patchinfo.write( tlkSourceFile.getName() + " " + src.size() + "-" + (src.size()+patch.size()-1) + lineSeparator );
                    src.addAll( patch );
                    src.saveAs( tlkOutputFile, Version.getDefaultVersion() );
                    System.out.println("tlk file saved");
                }
            } else {
                System.out.println("no tlk patch found");
            }
            offsetMap.put("tlk", tlkOffset + (isUserTlk? TlkLookup.USERTLKOFFSET : 0));

            //List includeScripts = new Vector();
            String includefilename = "patchdefinitions";
            //File nwscriptFile = new File( sourceRep, "nwscript.nss" );
            final InputStream incIS = sourceRep.getResource(new ResourceID(includefilename, "nss"));
            final File includeFile;
            /*
            if ( nwscriptFile.exists() ){
            includeFile = new File(outputDir, "nwscript.nss");
            filecopy( nwscriptFile, includeFile );
            }
            */
            if (incIS != null) {
                includeFile = new File(outputDir, includefilename + ".nss");
                Files.copy(incIS, includeFile.toPath(), REPLACE_EXISTING);
            } else {
                includeFile = new File(outputDir, "patchdefinitions.nss");
                includeFile.createNewFile();
            }

            // apply 2da patches, update references and write constant definitions
            for (int i = 0; i < daPatchFiles.length; i++) {
                final String patchName = daPatchFiles[i].getName();
                final daReference[] refs = refMap.get(patchName.toLowerCase());
                if (refs == null) {
                    System.out.println("no reference definition found for file : " + patchName);
                } else {
                    System.out.println(
                        "updating 2da references for patch file "
                            + patchName
                            + " ----------------------------------");
                    for (final daReference ref : refs) {
                        Integer shift = offsetMap.get(ref.target);
                        if (shift == null) {
                            System.out.println("no patch applied to " + ref.target + ", checking for absolute references");
                            shift = 0;
                        }
                        System.out.println("updating reference from column " + ref.column
                            + " into " + ref.target + " ( shift by " + shift + " )"
                        );
                        shiftReference(daPatchTables[i], shift.intValue(), ref.column, true);
                    }
                }
                final daReference cdef = cdefMap.get(patchName.toLowerCase());
                if (cdef != null) {
                    // write constant definitions, not neccessary if joining patches
                    //File includeFile = new File(outputDir, cdef.target);
                    writeScriptConstants(
                        daPatchTables[i],
                        includeFile,
                        cdef.column,
                        offsetMap.get(patchName.toLowerCase()).intValue(),
                        true);
                    //includeScripts.add( cdef.target );
                }
                if (daSourceTables[i] == null) {
                    daSourceTables[i] = new TwoDaTable(daPatchTables[i]);
                    // new empty table
                    patch2da(daSourceTables[i], daPatchTables[i], false);
                    //patchinfo.write( daPatchFiles[i].getName() + " " + daSourceTables[i].getRowCount() + "-" + (daSourceTables[i].getRowCount()+countNonAbsoluteLines(daPatchTables[i])) + lineSeparator );
                    // can't set absolute line position if source unavailable
                } else {
                    // fill up to minimum size ...
                    final Integer minsize = minsizes.get(patchName.toLowerCase());
                    if (minsize != null) {
                        for (int fill = daSourceTables[i].getRowCount();
                            fill < minsize.intValue();
                            ++fill
                        ) {
                            daSourceTables[i].appendRow(daSourceTables[i].emptyRow());
                            daSourceTables[i].setValueAt(Integer.toString(fill), fill, 0);
                        }
                    }
                    final int count = daSourceTables[i].getRowCount();
                    patchinfo.write(patchName + " " + count + "-"
                        + (count + countNonAbsoluteLines(daPatchTables[i])-1)
                        + lineSeparator
                    );
                    patch2da(daSourceTables[i], daPatchTables[i], true);
                }
                //racialtypes.nss needs some special treatment now ( damn ) ...........
                /* nwn v1.30 doesn't allow replacing of nwscript.nss anymore,
                 * so using RACIAL_TYPE_ALL / INVALID is no longer possible
                 * (cannot assign values in an include file )
                 */
                if (false && cdef != null) {
                    if (patchName.equalsIgnoreCase("racialtypes.2da")) {
                        final int lastRace = daSourceTables[i].getRowCount();
                        String script = "";
                        boolean append = true;
                        //if ( nwscriptFile.exists() ){
                        if (incIS != null) {
                            // need to read the script and remove existing RACIAL_TYPE_ALL and
                            // RACIAL_TYPE_INVALID definitions
                            append = false;
                            final StringBuilder sb = new StringBuilder();
                            try (final BufferedReader in = new BufferedReader(new FileReader(includeFile))) {
                                String line;
                                while ((line = in.readLine()) != null) {
                                    sb.append(line);
                                    sb.append(lineSeparator);
                                }
                            }
                            script = sb.toString();
                            //System.out.println(script);
                            Matcher mat =
                                Pattern.compile(
                                    "int\\s+RACIAL_TYPE_ALL\\s*=").matcher(
                                        script);
                            mat.find();
                            script = mat.replaceAll("//" + mat.group());
                            mat =
                                Pattern.compile(
                                    "int\\s+RACIAL_TYPE_INVALID\\s*=").matcher(
                                        script);
                            mat.find();
                            script = mat.replaceAll("//" + mat.group());
                        }
                        try (final FileWriter out = new FileWriter(includeFile, append)) {
                            out.write(script);
                            out.write(
                                "const int RACIAL_TYPE_ALL = "
                                    + (lastRace)
                                    + ";"
                                    + lineSeparator);
                            out.write(
                                "const int RACIAL_TYPE_INVALID = "
                                    + (lastRace + 1)
                                    + ";"
                                    + lineSeparator);
                            out.write(lineSeparator);
                        }
                    }
                }
                if (false && cdef != null) {//unused !
                    if (patchName.equalsIgnoreCase("racialtypes.2da")) {
                        final int lastRace = daSourceTables[i].getRowCount();
                        try (final FileWriter out = new FileWriter(includeFile, true)) {
                            out.write(
                                "RACIAL_TYPE_ALL = "
                                    + (lastRace)
                                    + ";"
                                    + lineSeparator);
                            out.write(
                                "RACIAL_TYPE_INVALID = "
                                    + (lastRace + 1)
                                    + ";"
                                    + lineSeparator);
                            out.write(lineSeparator);
                        }
                    }
                }
            }

            // save files .........................................................
            System.out.println("saving 2da files to dir " + outputDir.getAbsolutePath());
            for (int i = 0; i < daPatchFiles.length; i++)
                daSourceTables[i].writeToFile(new File(outputDir, daPatchFiles[i].getName()));

            // compile scripts ...............................................
            if (compile) {
                /*
                FileWriter out = new FileWriter( new File( outputDir, "patchdefs.nss"), true ); //append
                for ( int j = 0; j < includeScripts.size(); j++ )
                out.write( "#include \"" + includeScripts.get(j) + "\"" + lineSeparator );
                out.close();
                */
                try {
                    System.out.println("trying to compile scripts ...");
                    File[] scripts = outputDir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith(".nss");
                        }
                    });
                    for (final File script : scripts) {
                        if (script.getName().equalsIgnoreCase("nwscript.nss")) {
                            continue;
                        }
                        if (script.getName().equalsIgnoreCase("patchdefinitions.nss")) {
                            continue;
                        }
                        compileScript(script, nwnDir, outputDir);
                    }

                } catch (Exception ex) {
                    System.out.println("exception during scripts compilation : ");
                    ex.printStackTrace();
                }
            }

            // build hak file ...........................................
            if (buildHak) {
                System.out.println("writing hak file");
                File hakDir = new File(outputDir, "hak");
                hakDir.mkdir();
                ErfFile erf = new ErfFile( new File(hakDir, patchDir.getName() + ".hak"), ErfFile.HAK, new GffCExoLocString("foo") );
                for (final File file : outputDir.listFiles()) {
                    if (file.isFile()) {
                        erf.putResource(file);
                    }
                }
                erf.write();
            }
            patchinfo.flush();
        }
		System.out.println("done");
	}

	private static int countNonAbsoluteLines( TwoDaTable t ){
		int r = 0;
		for ( int i = 0; i < t.getRowCount(); i++ )
			if ( !t.getValueAt( i,0 ).startsWith("!") )
				r++;
		return r;
	}

    public static void joinPatches(List<String> inputpatches, File outputDir) throws IOException {
        final String[] patchdirnames = new String[inputpatches.size()];
		// reverse order of arguments
		for (int i = 0; i < patchdirnames.length; i++)
            patchdirnames[i] = inputpatches.get(patchdirnames.length - 1 - i);
		File[] outputdirs = new File[patchdirnames.length - 1];
		for (int i = 0; i < outputdirs.length - 1; i++) {
			outputdirs[i] = File.createTempFile("nwnpatch", null);
			outputdirs[i].delete();
			outputdirs[i].mkdirs();
			outputdirs[i].deleteOnExit();
		}
		outputdirs[outputdirs.length - 1] = outputDir;
		joinPatches(
			new File(patchdirnames[0]),
			new File(patchdirnames[1]),
			outputdirs[0]);
		for (int i = 2; i < patchdirnames.length; i++)
			joinPatches(
				outputdirs[i - 2],
				new File(patchdirnames[i]),
				outputdirs[i - 1]);
		// delete temp dirs
		for (int i = 0; i < outputdirs.length - 1; i++)
			delRek(outputdirs[i]);

	}

    /**
     * Append patch in {@code patchDir1} to the patch in {@code patchDir2},
     * write new patch to {@code joinedDir}
	 */
	public static void joinPatches(
		File patchDir1,
		File patchDir2,
		File joinedDir)
		throws IOException {
		if (!joinedDir.exists())
			joinedDir.mkdirs();
		System.out.println(
			"joining patches in "
				+ patchDir1
				+ " and "
				+ patchDir2
				+ ", write to "
				+ joinedDir);

		// remove files in output dir
		/*
		File[] rem = joinedDir.listFiles();
		for (int i = 0; i < rem.length; i++)
			if (rem[i].isFile())
				rem[i].delete();
		*/
		// copy files from script dirs
		System.out.println("copying scripts ...");
		File scriptDir1 = new File(patchDir1, "scripts");
		File scriptDirNew = new File(joinedDir, "scripts");
		if (scriptDir1.exists()) {
			scriptDirNew.mkdirs();
            for (final File file : scriptDir1.listFiles()) {
                if (file.isFile()) {
                    filecopy(file, new File(scriptDirNew, file.getName()));
                }
            }
		}
		File scriptDir2 = new File(patchDir2, "scripts");
		if (scriptDir2.exists()) {
			scriptDirNew.mkdirs();
            for (final File file : scriptDir2.listFiles()) {
                if (file.isFile()) {
                    filecopy(file, new File(scriptDirNew, file.getName()));
                }
            }
		}
		// copy contents of patchDir1 to joinedDir
		System.out.println("copying patch2 2da files ...");
        for (final File file : patchDir2.listFiles()) {
            if (file.isFile()) {
                filecopy(file, new File(joinedDir, file.getName()));
            }
        }
        // read reference file ...............
        final Map<String, daReference[]> refMap = readReferenceDefs(new File(patchDir1, referencesFilename));
        final Map<String, daReference[]> defaultRefMap =
            readReferenceDefs(
                new InputStreamReader(
                    ClassLoader.getSystemResourceAsStream(
                        "defaultreferences.txt")));
		// adding reference defaults to patch definitions ( overwriting patch definitions )
		refMap.putAll(defaultRefMap);

		System.out.println(
			"reading 2da files from " + patchDir1.getAbsolutePath());
		File[] daPatchFiles = patchDir1.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".2da");
			}
		});
		TwoDaTable[] daPatchTables = new TwoDaTable[daPatchFiles.length];
		for (int i = 0; i < daPatchFiles.length; i++) {
			daPatchTables[i] = new TwoDaTable(daPatchFiles[i]);
			System.out.println(daPatchFiles[i].getName().toLowerCase());
		}
		//	note : daPatchFiles[i] <--> daPatchTables[i],
		//  i.e. daPatchFiles[i].getName() is the filename for daPatchTables[i]

		// read the 2da source files
		System.out.println("reading 'source' 2da files : ");
		TwoDaTable[] daSourceTables = new TwoDaTable[daPatchFiles.length];
		for (int i = 0; i < daSourceTables.length; i++) {
			File src = new File(patchDir2, daPatchFiles[i].getName());
			if (!src.exists()) {
				//System.out.println( "couldn't find source file for " + src.getName() );
			} else {
				System.out.println(daPatchFiles[i].getName().toLowerCase());
				daSourceTables[i] = new TwoDaTable(src);
			}
		}
		// create the offset map for all the files
		// ( the offset is simply the number of lines in the source 2da file )
        final Map<String, Integer> offsetMap = new TreeMap<>();
		for (int i = 0; i < daSourceTables.length; i++) {
            final String patchName = daPatchFiles[i].getName().toLowerCase();
			if (daSourceTables[i] != null) {
				int realLines = 0;
				// don't count absolute lines
				for (int r = 0; r < daSourceTables[i].getRowCount(); r++)
					if (!daSourceTables[i].getValueAt(r, 0).startsWith("!"))
						realLines++;
				offsetMap.put(patchName, realLines);
            } else {
                offsetMap.put(patchName, 0);
            }
		}

		File tlkPatch1 = new File(patchDir1, tlkPatchFilename);
		File tlkPatch2 = new File(patchDir2, tlkPatchFilename);
		File tlkJoined = new File(joinedDir, tlkPatchFilename);
		if (tlkPatch1.exists() && tlkPatch2.exists()) {
			offsetMap.put("tlk", TlkTool.getTlkFileSize(tlkPatch2));
			System.out.println("concatenating patch tlk files ...");
			TlkTool.concat(tlkPatch2, tlkPatch1, tlkJoined, TlkTool.TLKAPPEND);
		} else {
			if (tlkPatch1.exists())
				filecopy(tlkPatch1, tlkJoined);
			if (tlkPatch2.exists())
				filecopy(tlkPatch2, tlkJoined);
		}

		// same for difs
		File tlkDiff1 = new File(patchDir1, tlkUpdateFilename);
		File tlkDiff2 = new File(patchDir2, tlkUpdateFilename);
		File tlkDiffJoined = new File(joinedDir, tlkUpdateFilename);
		if (tlkDiff1.exists() && tlkDiff2.exists()) {
			System.out.println("merging diff tlk files ...");
			TlkContent c = new TlkContent( NwnLanguage.ENGLISH );
			int[] diffs1 = c.mergeDiff( tlkDiff1 );
			int[] diffs2 = c.mergeDiff( tlkDiff2 );
            final TreeSet<Integer> ts = new TreeSet<>();
			for (final int diff : diffs1)
				ts.add(diff);
			for (final int diff : diffs2)
				ts.add(diff);
			int[] newDiffs = new int[ ts.size() ];
			int itCount = 0;
            for (final Iterator<Integer> it = ts.iterator(); it.hasNext(); itCount++ )
                newDiffs[itCount] = it.next().intValue();
			c.writeDiff( tlkDiffJoined, newDiffs );
		} else {
			if (tlkDiff1.exists())
				filecopy(tlkDiff1, tlkDiffJoined);
			if (tlkDiff2.exists())
				filecopy(tlkDiff2, tlkDiffJoined);
		}

		for (int i = 0; i < daPatchFiles.length; i++) {
            final String patchName = daPatchFiles[i].getName();
            final daReference[] refs = refMap.get(patchName.toLowerCase());
            if (refs == null) {
                System.out.println("no reference definition found for file : " + patchName);
			} else {
				System.out.println(
					"updating 2da references for patch file "
						+ patchName
						+ " ----------------------------------");
                for (final daReference ref : refs) {
                    Integer shift = offsetMap.get(ref.target);
                    if (shift == null) {
                        System.out.println("no patch applied to " + ref.target);
                        shift = 0;
                    }
                    System.out.println("updating reference from column " + ref.column
                        + " into " + ref.target + " ( shift by " + shift + " )"
                    );
                    shiftReference(daPatchTables[i], shift.intValue(), ref.column, false);
                }
			}
			if (daSourceTables[i] == null)
				daSourceTables[i] = new TwoDaTable(daPatchTables[i]);
			// new empty table
			patch2da(daSourceTables[i], daPatchTables[i], false);
		}

		System.out.println("writing joined 2da files ...");
		for (int i = 0; i < daPatchFiles.length; i++)
			daSourceTables[i].writeToFile(
				new File(joinedDir, daPatchFiles[i].getName()));

		System.out.println(
			"concatenating references.txt and  includedefs.txt ...");
		File ref1file = new File(patchDir1, referencesFilename);
		File ref2file = new File(patchDir2, referencesFilename);
		catTextFiles(
			new File[] { ref1file, ref2file },
			new File(joinedDir, referencesFilename),
			false);
		File cdef1file = new File(patchDir1, includedefFilename);
		File cdef2file = new File(patchDir2, includedefFilename);
		catTextFiles(
			new File[] { cdef1file, cdef2file },
			new File(joinedDir, includedefFilename),
			false);

		System.out.println("done");
	}
}
