package com.ericsson.commonutil.serialization;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 *
 * @author aopkarja
 */
public class DublicateDetectionModule extends SimpleModule {

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.insertAnnotationIntrospector(new DublicateIntrospector());
    }

}
