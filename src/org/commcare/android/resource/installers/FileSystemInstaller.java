/**
 * 
 */
package org.commcare.android.resource.installers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.util.StreamUtil;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public abstract class FileSystemInstaller implements ResourceInstaller<AndroidCommCarePlatform> {
	String localLocation;
	String localDestination;
	String upgradeDestination;
	
	public FileSystemInstaller() {
		
	}
	
	public FileSystemInstaller(String localDestination, String upgradeDestination) {
		this.localDestination = localDestination;
		this.upgradeDestination = upgradeDestination;
	}
	
	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#cleanup()
	 */
	public void cleanup() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#initialize(org.commcare.util.CommCareInstance)
	 */
	public abstract boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException;

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#install(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceLocation, org.javarosa.core.reference.Reference, org.commcare.resources.model.ResourceTable, org.commcare.util.CommCareInstance, boolean)
	 */
	public boolean install(Resource r, ResourceLocation location, Reference ref, ResourceTable table, AndroidCommCarePlatform instance, boolean upgrade) throws UnresolvedResourceException, UnfullfilledRequirementsException {
		localLocation = (upgrade ? upgradeDestination : localDestination) + "/" + r.getResourceId() + ".xml";
		try {
			//Stream to location
			Reference local = ReferenceManager._().DeriveReference(localLocation);
			StreamUtil.transfer(ref.getStream(), local.getOutputStream());
			
			int status = customInstall(local, upgrade);
			
			table.commit(r, status);
			return true;
		} catch (InvalidReferenceException e) {
			e.printStackTrace();
			throw new UnresolvedResourceException(r, "Unavailable resource at " + e.getReferenceString());
		} catch (IOException e) {
			e.printStackTrace();
			throw new UnresolvedResourceException(r, "IOException for resource");
		}
	}

	protected abstract int customInstall(Reference local, boolean upgrade) throws IOException;
	
	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#requiresRuntimeInitialization()
	 */
	public abstract boolean requiresRuntimeInitialization();

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#uninstall(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable, org.commcare.resources.model.ResourceTable)
	 */
	public boolean uninstall(Resource r, ResourceTable table, ResourceTable incoming) throws UnresolvedResourceException {
		try{
			return new File(ReferenceManager._().DeriveReference(this.localLocation).getLocalURI()).delete();
		} catch(InvalidReferenceException e) {
			throw new UnresolvedResourceException(r, "Local reference couldn't be found for resource at " + this.localLocation);
		}
	}

	/* (non-Javadoc)
	 * @see org.commcare.resources.model.ResourceInstaller#upgrade(org.commcare.resources.model.Resource, org.commcare.resources.model.ResourceTable)
	 */
	public boolean upgrade(Resource r, ResourceTable table) throws UnresolvedResourceException {
		try {
			
			//Get the temporary location
			Reference local = ReferenceManager._().DeriveReference(localLocation);

			//Get final destination
			String finalLocation =  localDestination + "/" + r.getResourceId() + ".xml";
			Reference finalRef = ReferenceManager._().DeriveReference(finalLocation);
			
			if(!(new File(local.getLocalURI()).renameTo(new File(finalRef.getLocalURI())))) {
				return false;
			}
			
			localLocation = finalLocation;
			return true;
		} catch (InvalidReferenceException e) {
			e.printStackTrace();
			throw new UnresolvedResourceException(r, "Invalid reference while upgrading local resource. Reference path is: " + e.getReferenceString());
		}
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
	 */
	public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
		this.localLocation = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
		this.localDestination = ExtUtil.readString(in);
		this.upgradeDestination = ExtUtil.readString(in);
	}

	/* (non-Javadoc)
	 * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
	 */
	public void writeExternal(DataOutputStream out) throws IOException {
		ExtUtil.writeString(out, ExtUtil.emptyIfNull(localLocation));
		ExtUtil.writeString(out, localDestination);
		ExtUtil.writeString(out, upgradeDestination);
	}
}
