/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import com.google.gwt.http.client.URL;

import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class QueryParameters
{
    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Location")
    private static class LocationLike {
        @JsProperty native String getSearch();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private static class WindowLike {
        @JsProperty native LocationLike getLocation();
    }

    @JsProperty(namespace = JsPackage.GLOBAL, name = "window")
    private static native WindowLike getWindow();

    private HashMap<String, String> map = new HashMap<String, String>();

    public QueryParameters()
    {
        String search = getQueryString();
        if ((search != null) && (search.length() > 0))
        {
            String[] nameValues = search.substring(1).split("&");
            for (int i = 0; i < nameValues.length; i++)
            {
                String[] pair = nameValues[i].split("=");

                map.put(pair[0], URL.decode(pair[1]));
            }
        }
    }
    
    public String getValue(String key)
    {
        return (String) map.get(key);
    }
    

    
    public boolean getBooleanValue(String key, boolean def){
    	String val=getValue(key);
    	if (val==null)
    		return def;
    	else
    		return (val=="1" || val.equalsIgnoreCase("true"));
    }
    
    private String getQueryString() {
        WindowLike window = getWindow();
        if (window == null || window.getLocation() == null)
            return "";
        String search = window.getLocation().getSearch();
        return (search == null) ? "" : search;
    }
}
