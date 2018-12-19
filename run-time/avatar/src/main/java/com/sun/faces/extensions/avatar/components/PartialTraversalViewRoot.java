/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

/*
 * PartialTraversalViewRoot.java
 *
 * Created on January 23, 2007, 12:51 PM
 *
 */

/*
 * $Id: PartialTraversalViewRoot.java,v 1.4 2005/12/21 22:38:09 edburns Exp $
 */

package com.sun.faces.extensions.avatar.components;

import java.io.IOException;
import javax.faces.context.FacesContext;

/**
 *
 * @author edburns
 */
public interface PartialTraversalViewRoot {
    void encodePartialResponseBegin(FacesContext context) throws IOException;

    void encodePartialResponseEnd(FacesContext context) throws IOException;

    void postExecuteCleanup(FacesContext context);
    
}