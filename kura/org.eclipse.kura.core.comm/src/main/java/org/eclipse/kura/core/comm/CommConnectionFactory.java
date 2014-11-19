/**
 * Copyright (c) 2011, 2014 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eurotech
 */
package org.eclipse.kura.core.comm;

import java.io.IOException;

import javax.microedition.io.Connection;

import org.eclipse.kura.comm.CommConnection;
import org.eclipse.kura.comm.CommURI;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.io.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommConnectionFactory implements ConnectionFactory 
{
	private static final Logger s_logger = LoggerFactory.getLogger(CommConnectionFactory.class);

	@SuppressWarnings("unused")
	private ComponentContext      m_ctx;
	
	private static CommConnection cached = null;

	// ----------------------------------------------------------------
	//
	//   Dependencies
	//
	// ----------------------------------------------------------------

	
	
	// ----------------------------------------------------------------
	//
	//   Activation APIs
	//
	// ----------------------------------------------------------------
	
	protected void activate(ComponentContext componentContext) 
	{			
		//
		// save the bundle context
		m_ctx = componentContext;
	}
	
	
	protected void deactivate(ComponentContext componentContext) 
	{
		m_ctx = null;
	}
	
	public Connection createConnection(String name, int mode, boolean timeouts)
		throws IOException
	{
		if (cached == null) {
			try {
				CommURI uri = CommURI.parseString(name);
				s_logger.info("Retrieving communication service...");
				BundleContext bc = m_ctx.getBundleContext();
				ServiceReference<?> sr = bc
						.getServiceReference(CommConnection.class);
				if (sr != null) {
					
					s_logger.info("Registered CommConnection Service ({})found. Instantiating...", sr.toString());
					
					cached = (CommConnection) bc.getService(sr);
					cached.setCommURI(uri);

				} else {
					try {
						s_logger.info("No registered CommConnection Service found. Falling back to default...");

						cached = new CommConnectionImpl(uri, mode, timeouts);
					} catch (Throwable t) {
						throw new IOException(t);
					}
				}
			} catch (Throwable ex) {
				throw new IOException(ex);
			}

		}
		return cached;
	}
}
