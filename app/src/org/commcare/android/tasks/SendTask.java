/**
 * 
 */
package org.commcare.android.tasks;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ProcessAndSendTask.ProcessIssues;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.CommCareFormDumpActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.locale.Localization;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;
import org.commcare.android.database.user.models.User;

/**
 * @author ctsims
 *
 */
public abstract class SendTask extends CommCareTask<FormRecord, String, Boolean, CommCareFormDumpActivity>{

	Context c;
	String url;
	Long[] results;
	
	ProcessTaskListener listener;
	DataSubmissionListener formSubmissionListener;
	CommCarePlatform platform;
	
	SqlStorage<FormRecord> storage;
	TextView outputTextView;
	 // 5MB less 1KB overhead
	
	public SendTask(Context c, CommCarePlatform platform, String url, TextView outputTextView) throws SessionUnavailableException{
		this.c = c;
		this.url = url;
		storage =  CommCareApplication._().getUserStorage(FormRecord.class);
		this.outputTextView = outputTextView;
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
		url = null;
		results = null;
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

	@Override
	protected Boolean doTaskBackground(FormRecord... params) {
		
		publishProgress(Localization.get("bulk.form.send.start"));
		
		//get external SD folder
		
		ArrayList<String> externalMounts = FileUtil.getExternalMounts();
		String baseDir = externalMounts.get(0);
		String folderName = Localization.get("bulk.form.foldername");
		File dumpDirectory = new File( baseDir + "/" + folderName);
		
		//sanity check
		if(!(dumpDirectory.isDirectory())){
			return false;
		}
		
		File[] files = dumpDirectory.listFiles();
		int counter = 0;
		
		results = new Long [files.length];
		
		for(int i = 0; i < files.length ; ++i ) {
			//Assume failure
			results[i] = FormUploadUtil.FAILURE;
		}
		
		boolean allSuccessful = true;
		
		for(int i=0;i<files.length;i++){
			
			publishProgress(Localization.get("bulk.send.dialog.progress",new String[]{""+ (i+1)}));
			
			File f = files[i];
			
			if(!(f.isDirectory())){
				return false;
			}
			try{
				User user = CommCareApplication._().getSession().getLoggedInUser();
				results[i] = FormUploadUtil.sendInstance(counter,f,url, user);
				if(results[i] == FormUploadUtil.FULL_SUCCESS){
					FileUtil.deleteFile(f);
				}
				else if(results[i] == FormUploadUtil.TRANSPORT_FAILURE){
					allSuccessful = false;
					publishProgress(Localization.get("bulk.send.transport.error"));
				}
				else{
					allSuccessful = false;
					publishProgress(Localization.get("bulk.send.file.error", new String[] {f.getAbsolutePath()}));
				}
				counter++;
			} catch(FileNotFoundException fe){
				Log.e("E", Localization.get("bulk.send.file.error", new String[] {f.getAbsolutePath()}), fe);
				publishProgress(Localization.get("bulk.send.file.error", new String[] {fe.getMessage()}));
			}
		}
		return allSuccessful;
	}
}