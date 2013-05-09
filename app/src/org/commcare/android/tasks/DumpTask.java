/**
 * 
 */
package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ProcessAndSendTask.ProcessIssues;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.ReflectionUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.CommCareFormDumpActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.StorageFullException;

import android.content.Context;
import android.os.Environment;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public abstract class DumpTask extends CommCareTask<String, String, Boolean, CommCareFormDumpActivity>{

	Context c;
	Long[] results;
	File dumpFolder;
	
	public static final long FULL_SUCCESS = 0;
	public static final long PARTIAL_SUCCESS = 1;
	public static final long FAILURE = 2;
	public static final long TRANSPORT_FAILURE = 4;
	public static final long PROGRESS_ALL_PROCESSED = 8;
	
	public static final long SUBMISSION_BEGIN = 16;
	public static final long SUBMISSION_START = 32;
	public static final long SUBMISSION_NOTIFY = 64;
	public static final long SUBMISSION_DONE = 128;
	
	public static final long PROGRESS_LOGGED_OUT = 256;
	public static final long PROGRESS_SDCARD_REMOVED = 512;
	
	public static final int BULK_DUMP_ID = 23456;
	
	ProcessTaskListener listener;
	DataSubmissionListener formSubmissionListener;
	CommCarePlatform platform;
	
	SqlStorage<FormRecord> storage;
	TextView outputTextView;
	
	private static long MAX_BYTES = (5 * 1048576)-1024; // 5MB less 1KB overhead
	
	public DumpTask(Context c, CommCarePlatform platform, TextView outputTextView) throws SessionUnavailableException{
		this.c = c;
		storage =  CommCareApplication._().getUserStorage(FormRecord.class);
		this.outputTextView = outputTextView;
		taskId = DumpTask.BULK_DUMP_ID;
		platform = this.platform;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	protected void onProgressUpdate(String... values) {
		super.onProgressUpdate(values);
	}
	
	public void setListeners(ProcessTaskListener listener, DataSubmissionListener submissionListener) {
		this.listener = listener;
		this.formSubmissionListener = submissionListener;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);
		//These will never get Zero'd otherwise
		c = null;
		results = null;
	}
	
	private static final String[] SUPPORTED_FILE_EXTS = {".xml", ".jpg", ".3gpp", ".3gp"};
	
	private long dumpInstance(int submissionNumber, File folder, SecretKeySpec key) throws FileNotFoundException {
		
        File[] files = folder.listFiles();
        
        File myDir = new File(dumpFolder, folder.getName());
        myDir.mkdirs();
        
        if(files == null) {
        	//make sure external storage is available to begin with.
        	String state = Environment.getExternalStorageState();
        	if (!Environment.MEDIA_MOUNTED.equals(state)) {
        		//If so, just bail as if the user had logged out.
        		throw new SessionUnavailableException("External Storage Removed");
        	} else {
        		throw new FileNotFoundException("No directory found at: " + folder.getAbsoluteFile());
        	}
        } 

        //If we're listening, figure out how much (roughly) we have to send
		long bytes = 0;
        for (int j = 0; j < files.length; j++) {
        	//Make sure we'll be sending it
        	boolean supported = false;
        	for(String ext : SUPPORTED_FILE_EXTS) {
        		if(files[j].getName().endsWith(ext)) {
        			supported = true;
        			break;
        		}
        	}
        	if(!supported) { continue;}
        	
        	bytes += files[j].length();
        }
        
		//this.startSubmission(submissionNumber, bytes);
		
		final Cipher decrypter = FormUploadUtil.getDecryptCipher(key);
		
		for(int j=0;j<files.length;j++){
			
			File f = files[j];
			// This is not the ideal long term solution for determining whether we need decryption, but works
			if (f.getName().endsWith(".xml")) {
				try{
					FileUtil.copyFile(f, new File(myDir, f.getName()), decrypter, null);
				}
				catch(IOException ie){
					publishProgress(("File writing failed: " + ie.getMessage()));
					return ProcessAndSendTask.FAILURE;
				}
			}
			else{
				try{
					FileUtil.copyFile(f, new File(myDir, f.getName()));
				}
				catch(IOException ie){
					publishProgress(("File writing failed: " + ie.getMessage()));
					return ProcessAndSendTask.FAILURE;
				}
			}
		}
        return ProcessAndSendTask.FULL_SUCCESS;
	}
	
	@Override
	protected Boolean doTaskBackground(String... params) {
		
		// ensure that SD is available, writable, and not emulated

		boolean mExternalStorageAvailable = false;
		boolean mExternalStorageWriteable = false;
		
		boolean mExternalStorageEmulated = ReflectionUtil.mIsExternalStorageEmulatedHelper();
		
		String state = Environment.getExternalStorageState();
		
		ArrayList<String> externalMounts = FileUtil.getExternalMounts();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
		    // We can read and write the media
		    mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
		    // We can only read the media
		    mExternalStorageAvailable = true;
		    mExternalStorageWriteable = false;
		} else {
		    // Something else is wrong. It may be one of many other states, but all we need
		    //  to know is we can neither read nor write
		    mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
		
		if(!mExternalStorageAvailable){
			publishProgress(Localization.get("bulk.form.sd.unavailable"));
			return false;
		}
		if(!mExternalStorageWriteable){
			publishProgress(Localization.get("bulk.form.sd.unwritable"));
			return false;
		}
		if(mExternalStorageEmulated && externalMounts.size() == 0){
			publishProgress(Localization.get("bulk.form.sd.emulated"));
			return false;
		}
		
		String baseDir = externalMounts.get(0);
		String folderName = Localization.get("bulk.form.foldername");
		
		File f = new File( baseDir + "/" + folderName);
		
		if(f.exists() && f.isDirectory()){
			f.delete();
		}
		
		File dumpDirectory = new File(baseDir + "/" + folderName);
		dumpDirectory.mkdirs();
		
    	SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
    	
    	//Get all forms which are either unsent or unprocessed
    	Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
    	ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
    	
    	if(ids.size() > 0) {
    		FormRecord[] records = new FormRecord[ids.size()];
    		for(int i = 0 ; i < ids.size() ; ++i) {
    			records[i] = storage.read(ids.elementAt(i).intValue());
    		}

    		dumpFolder = dumpDirectory;

    		try{
    			
    			results = new Long[records.length];
    			for(int i = 0; i < records.length ; ++i ) {
    				//Assume failure
    				results[i] = ProcessAndSendTask.FAILURE;
    			}
    			
    			publishProgress(Localization.get("bulk.form.start"));
    			
    			for(int i = 0 ; i < records.length ; ++i) {
    				FormRecord record = records[i];
    				try{
    					//If it's unsent, go ahead and send it
    					if(FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
    						File folder;
    						try {
    							folder = new File(record.getPath(c)).getCanonicalFile().getParentFile();
    						} catch (IOException e) {
    							Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Bizarre. Exception just getting the file reference. Not removing." + getExceptionText(e));
    							continue;
    						}
    						
    						//Good!
    						//Time to Send!
    						try {
    							results[i] = dumpInstance(i, folder, new SecretKeySpec(record.getAesKey(), "AES"));
    							
    						} catch (FileNotFoundException e) {
    							if(CommCareApplication._().isStorageAvailable()) {
    								//If storage is available generally, this is a bug in the app design
    								Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
    							} else {
    								//Otherwise, the SD card just got removed, and we need to bail anyway.
    								CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
    								break;
    							}
    							continue;
    						}
    					
    						//Check for success
    						if(results[i].intValue() == ProcessAndSendTask.FULL_SUCCESS) {
    						    new FormRecordCleanupTask(c, platform).wipeRecord(record);
    						    publishProgress(Localization.get("bulk.form.dialog.progress",new String[]{""+i, ""+results[i].intValue()}));
    				        }
    					}
    					
    					
    				} catch (StorageFullException e) {
    					Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Really? Storage full?" + getExceptionText(e));
    					throw new RuntimeException(e);
    				} catch(SessionUnavailableException sue) {
    					throw sue;
    				} catch (Exception e) {
    					//Just try to skip for now. Hopefully this doesn't wreck the model :/
    					Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Totally Unexpected Error during form submission" + getExceptionText(e));
    					continue;
    				}  
    			}
    			
    			long result = 0;
    			for(int i = 0 ; i < records.length ; ++ i) {
    				if(results[i] > result) {
    					result = results[i];
    				}
    			}
    			
    			//this.endSubmissionProcess();
    			
    			} 
    			catch(SessionUnavailableException sue) {
    				this.cancel(false);
    				return false;
    			}
    		
    		//
    		//
    		return true;
    	} else {
    		publishProgress(Localization.get("bulk.form.no.unsynced"));
    		return false;
    	}
	}

	private String getExceptionText (Exception e) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			e.printStackTrace(new PrintStream(bos));
			return new String(bos.toByteArray());
		} catch(Exception ex) {
			return null;
		}
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onCancelled()
	 */
	@Override
	protected void onCancelled() {
		super.onCancelled();
		if(this.formSubmissionListener != null) {
			formSubmissionListener.endSubmissionProcess();
		}
		CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
	}

}
