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

/**
 * This exception is for some severe unexplained things
 * 
 * @author Igor Polevoy
 */
public class InternalException  extends RuntimeException{
    public InternalException(Throwable cause) {
        super(cause);  
    }

    public InternalException(String message) {
        super(message);
    }
}
