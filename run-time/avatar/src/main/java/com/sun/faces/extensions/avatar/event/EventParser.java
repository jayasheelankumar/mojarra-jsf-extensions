/*
 * Copyright (c) 2006, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.faces.extensions.avatar.event;

import com.sun.faces.extensions.avatar.lifecycle.AsyncResponse;
import com.sun.faces.extensions.common.util.Util;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.faces.FacesException;
import javax.faces.application.Application;
import javax.faces.component.ContextCallback;
import javax.faces.component.UIComponent;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.event.FacesEvent;
import javax.faces.event.PhaseId;

/**
 *
 * <p>Convenience methods for extracting <code>FacesEvent</code> related
 * information from the incoming request.</p>
 *
 * @author edburns
 */
public class EventParser {
    
    /** Creates a new instance of EventParser */
    public EventParser() {
    }


    /**
     * <p>Look in the {@link #FACES_EVENT_HEADER} and parse it according to 
     * the following syntax</p>
     *
     * <code><pre>EVENT_TYPE,clientId,PhaseId[,eventCtorArgs...],...[;EVENT_TYPE,clientId,PhaseId,...]*</pre></code>
     *
     * <p>Where <code>EVENT_TYPE</code> is the String <code>ValueChangeEvent</code>,
     * <code>ActionEvent</code>, or one of the event types defined to the system
     * in the 
     * that must be a subclass of <code>FacesEvent</code>.
     *
     */
    
    public static void queueFacesEvents(FacesContext context) {
        Map<String, String> p = 
                FacesContext.getCurrentInstance().getExternalContext()
                .getRequestHeaderMap();
        List<FacesEvent> result = new ArrayList<FacesEvent>();
        String header = p.get(AsyncResponse.FACES_EVENT_HEADER);
        String [] events = null;
        String [] params = null;
        String [] ctorArgs = null;
        PhaseId phaseId = null;
        int i = 0, j = 0;
        if (header != null) {
            events = header.split(";");
            for (i = 0; i < events.length; i++) {
                params = events[i].split(",");
                phaseId = Util.getPhaseIdFromString(params[2]);
                // If we only have the EVENT_TYPE, clientId, and PhaseId...
                if (3 == params.length) {
                    // pass no ctor args.
                    queueFacesEvent(context, params[0], params[1], phaseId, 
                            new String[0]);
                }
                // Otherwise, if we have more than just those three pieces of data...
                else if (3 < params.length) {
                    // assume they are argument values
                    ctorArgs = new String[params.length - 3];
                    System.arraycopy(params, 3, ctorArgs, 0, ctorArgs.length);
                    queueFacesEvent(context, params[0], params[1], phaseId, ctorArgs);
                }
                else {
                    // Log Message.  Params must be >= 3
                }
            }
        }
    }

    /**
     * <p>Given the argument <code>phaseId</code> consult the incoming request and see if there is a </p>
     */

    public static void invokeComponentMethodCallbackForPhase(FacesContext context,
            PhaseId curPhase) {
        ExternalContext extContext = context.getExternalContext();
        Map<String, String> headersMap = extContext.getRequestHeaderMap();
        Map<String, Object> requestMap = extContext.getRequestMap();
        ComponentMethodCallback callback = null;
        final String REQUEST_MAP_KEY = "com.sun.faces.MethodExpressionCallback";

        if (null == (callback = 
                (ComponentMethodCallback) requestMap.get(REQUEST_MAP_KEY))) {
            String de = headersMap.get(AsyncResponse.METHOD_NAME_HEADER);
            if (de != null) {
                String[] ep = de.split(",");
                String clientId = ep[0];
                String method = (ep.length > 1) ? ep[1] : null;
                PhaseId phaseId = (ep.length > 2) ?
                    Util.getPhaseIdFromString(ep[2]) : PhaseId.RENDER_RESPONSE;

                callback = new ComponentMethodCallback(clientId, method, phaseId);
                requestMap.put(REQUEST_MAP_KEY, callback);
            }
        }
        if (null != callback && 0 == curPhase.compareTo(callback.getPhaseId())) {
            callback.invoke(context);
        }
    }

    private static void queueFacesEvent(FacesContext context,
            String eventId, String clientId, final PhaseId phaseId, 
            final String [] params) {
        FacesEvent result = null;
        Map<String,ConstructorWrapper> eventsMap = (Map<String,ConstructorWrapper>)
                context.getExternalContext().getApplicationMap().get(AsyncResponse.FACES_EVENT_CONTEXT_PARAM);
        
        assert(null != eventsMap);
        final ConstructorWrapper eventCtor = eventsMap.get(eventId);
        if (null != eventCtor) {
            
            context.getViewRoot().invokeOnComponent(context, clientId, new ContextCallback() {
                public void invokeContextCallback(FacesContext facesContext, 
                        UIComponent comp) {
                    FacesEvent event = null;
                    Class [] ctorArgClasses = eventCtor.getArgClasses();
                    int len = 0;
                    Object [] ctorArgs = getEventCtorArgs(facesContext, comp, 
                            ctorArgClasses, params);
                    try {
                        event = (FacesEvent) 
                                eventCtor.getConstructor().newInstance(ctorArgs);
                    } catch (InvocationTargetException ex) {
                        throw new FacesException(ex);
                    } catch (InstantiationException ex) {
                        throw new FacesException(ex);
                    } catch (IllegalAccessException ex) {
                        throw new FacesException(ex);
                    }
                    event.setPhaseId(phaseId);
                    comp.queueEvent(event);
                }
            });
        }
    }
    
    private static Object [] getEventCtorArgs(FacesContext context, UIComponent comp, 
            Class [] ctorArgClasses,
            String [] ctorArgValues) {
        Object [] result = null;
        Application app = context.getApplication();
        Converter converter = null;
        int argClassesLen = ctorArgClasses.length, 
            argValuesLen = ctorArgValues.length, 
            i = 0;
        

        // Special case heuristic.  Not sure if this is a good idea.
        
        // If ctorArgClasses.length == 1, ctorArgValues.length == 0
        // and ctorArgClasses[0] is a UIComponent, assume the only
        // argument is our comp argument.
        if (1 == argClassesLen && 0 == argValuesLen &&
            UIComponent.class.isAssignableFrom(ctorArgClasses[0])) {
            result = new Object[1];
            result[0] = comp;
        }
        // Otherwise, if the argument class list is a different length
        // than the argument values list...
        else if (argClassesLen != argValuesLen) {
            // return a result that indicates we cannot derive the 
            // constructor arguments.
            result = new Object[0];
        }
        else {
            // Otherwise, we know that the length of the argument class array
            // and the argument value array is the same.  Therefore, we assume
            // the ordering is the same and try to convert the values.
            assert(argClassesLen == argValuesLen);
            
            result = new Object[ctorArgClasses.length];

            // Iterate over both arrays and convert the elements using converters
            for (i = 0; i < ctorArgClasses.length; i++) {
                // First, check if this argument is an instanceof UIComponent...
                if (UIComponent.class.isAssignableFrom(ctorArgClasses[i])) {
                    // if so, assume it is intended to be the "source" argument.
                    result[i] = comp;
                }
                // Otherwise, try to use a converter to convert the type.
                else if (null != (converter = app.createConverter(ctorArgClasses[i]))) {
                    result[i] = converter.getAsObject(context, comp, ctorArgValues[i]);
                }
                // Otherwise, just use the unconverted value.
                else {
                    result[i] = ctorArgValues[i];
                }
            }
        }
        
        return result;
    }
    
}
