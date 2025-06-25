/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package imrcp.system;

import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * Contains convenience variables for common operations using the java nio 
 * package.
 * 
 * @author aaron.cherney
 */
public abstract class FileUtil
{
	/**
	 * File permissions used for creating directories
	 */
	public static FileAttribute[] DIRPERS = new FileAttribute[]{PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ,
		PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, 
		PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_READ, 
		PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE))};

	
	/**
	 * File permissions used for creating regular files
	 */
	public static FileAttribute[] FILEPERS = new FileAttribute[]{PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, 
		PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ))};

	
	/**
	 * Set of options used to open input streams that append to a new or 
	 * existing file
	 */
	public static Set<StandardOpenOption> APPENDTO = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

	
	/**
	 * Set of options used to open input streams that write to a new file or 
	 * replace an existing file
	 */
	public static Set<StandardOpenOption> WRITE = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	
	
	/**
	 * Array of options used to open input streams that write to a new file or 
	 * replace an existing file
	 */
	public static OpenOption[] WRITEOPTS = new OpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

	
	/**
	 * Array of options used to open input streams that append to a new or 
	 * existing file
	 */
	public static OpenOption[] APPENDOPTS = new OpenOption[]{StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND};
}
