package org.joget.marketplace;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(IteratorProcessTool.class.getName(), new IteratorProcessTool(), null));
        registrationList.add(context.registerService(IteratorProcessToolRecord.class.getName(), new IteratorProcessToolRecord(), null));
        registrationList.add(context.registerService(DatabaseQueryProcessTool.class.getName(), new DatabaseQueryProcessTool(), null));
        
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}