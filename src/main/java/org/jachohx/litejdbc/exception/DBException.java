/*
Copyright 2009-2014 Igor Polevoy

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/


package org.jachohx.litejdbc.exception;

import org.apache.commons.lang.StringUtils;

/**
 * Generic exception wrapper for all things DB.
 *
 * @author Igor Polevoy
 */
public class DBException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	String message;

    public DBException(Throwable cause) {
        super(cause);
        this.setStackTrace(cause.getStackTrace());
    }

    public DBException(String message) {
        super(message);
    }

    public DBException(String message, Throwable cause) {
        super(message, cause);
        this.setStackTrace(cause.getStackTrace());
    }


    /**
     *
     * @param query SQL query
     * @param params - array of parameters, can be null
     * @param cause real cause.
     */
    public DBException(String query, Object[] params, Throwable cause) {
        StringBuilder sb = new StringBuilder(cause.toString()).append(", query: ").append(query);
        if (params != null && params.length > 0) {
            sb.append(", params: ");
            sb.append(StringUtils.join(params, ", "));
        }
        message = sb.toString();
        setStackTrace(cause.getStackTrace());
        initCause(cause);
    }

    @Override
    public String getMessage() {
        return message == null ? super.getMessage() : message;
    }

    public DBException() {
        super();
    }
}
