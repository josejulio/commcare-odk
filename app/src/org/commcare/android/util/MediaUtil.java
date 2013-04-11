/**
 * 
 */
package org.commcare.android.util;

import java.io.IOException;

import org.commcare.android.javarosa.AndroidLogger;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * @author ctsims
 *
 */
public class MediaUtil {
	public static Bitmap getScaledImageFromReference(Context c, String jrReference) {
		//TODO: Eventually we'll want to be able to deal with dymanic resources here.
        try {

        Reference imageRef = ReferenceManager._().DeriveReference(jrReference);
        if(!imageRef.doesBinaryExist()) {
        	return null;
        }
        
        return BitmapFactory.decodeStream(imageRef.getStream());

        } catch(InvalidReferenceException ire) {
        	Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Invalid reference for an image: " + ire.getReferenceString());
        	return null;
        } catch(OutOfMemoryError oom) {
        	Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Out of memory loading reference: " + jrReference);
        	return null;
        } catch(IOException uie){
        	Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "IO Exception loading reference: " + jrReference);
        	return null;
        }
	}
}