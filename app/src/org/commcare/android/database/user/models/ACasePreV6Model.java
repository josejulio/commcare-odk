/**
 * 
 */
package org.commcare.android.database.user.models;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import org.commcare.cases.model.CaseIndex;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInstaller;
import org.commcare.resources.model.ResourceLocation;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;
import org.javarosa.core.util.externalizable.ExtWrapMapPoly;
import org.javarosa.core.util.externalizable.ExtWrapNullable;
import org.javarosa.core.util.externalizable.ExtWrapTagged;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * A model extension which reads Resource models from the
 * Version 6 db, and writes them back into the Version 7
 * db.
 * 
 * 
 * NOTE: This updater is _NOT ROBUST AGAINST METADATA
 * CHANGES_. If the Resource model metadata changes, this
 * needs to be modified to reflect the V6/7 metadata.  
 * 
 * @author ctsims
 *
 */
public class ACasePreV6Model extends ACase {
    
    /**
     Blank constructor for deserialization only!
    */
    public ACasePreV6Model() {
        
    }
    
    /* (non-Javadoc)
     * @see org.commcare.resources.model.Resource#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
     */
    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        typeId = ExtUtil.readString(in);
        id = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        name = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        closed = ExtUtil.readBool(in);
        dateOpened = (Date)ExtUtil.read(in, new ExtWrapNullable(Date.class), pf);
        recordId = ExtUtil.readInt(in);
        indices = (Vector<CaseIndex>)ExtUtil.read(in, new ExtWrapList(CaseIndexUpdater.class));
        data = (Hashtable)ExtUtil.read(in, new ExtWrapMapPoly(String.class, true), pf);
    }
    
    /*
     * (non-Javadoc)
     * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
     */
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, typeId);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(id));
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(name));
        ExtUtil.writeBool(out, closed);
        ExtUtil.write(out, new ExtWrapNullable(dateOpened));
        ExtUtil.writeNumeric(out, recordId);
        ExtUtil.write(out, new ExtWrapList(indices));
        ExtUtil.write(out, new ExtWrapMapPoly(data));
    }
    
    public static class CaseIndexUpdater extends CaseIndex {
        
        /*
         * serialization only!
         */
        public CaseIndexUpdater() {
            
        }

        /* (non-Javadoc)
         * @see org.javarosa.core.util.externalizable.Externalizable#readExternal(java.io.DataInputStream, org.javarosa.core.util.externalizable.PrototypeFactory)
         */
        public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
            mName = ExtUtil.readString(in);
            mTargetId = ExtUtil.readString(in);
            mTargetCaseType = ExtUtil.readString(in);
            mRelationship = CaseIndex.RELATIONSHIP_CHILD;
        }

        /* (non-Javadoc)
         * @see org.javarosa.core.util.externalizable.Externalizable#writeExternal(java.io.DataOutputStream)
         */
        public void writeExternal(DataOutputStream out) throws IOException {
            ExtUtil.writeString(out, mName);
            ExtUtil.writeString(out, mTargetId);
            ExtUtil.writeString(out, mTargetCaseType);
            ExtUtil.writeString(out, mRelationship);
        }
    }
}