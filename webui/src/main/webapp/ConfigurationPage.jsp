<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>
<%@ page import="com.muwire.webui.*" %>
<%@ page import="com.muwire.core.*" %>
<%@ page import="com.muwire.core.search.*" %>
<%@ page import="net.i2p.data.*" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@include file="initcode.jsi"%>

<% 
String pagetitle=Util._t("Configuration");
Core core = (Core) application.getAttribute("core");
%>

<html>
    <head>
<%@include file="css.jsi"%>
    </head>
    <body>
<%@include file="header.jsi"%>    	
	<aside>
<%@include file="searchbox.jsi"%>    	
<%@include file="sidebar.jsi"%>    	
	</aside>
	<section class="main foldermain">
		<form action="/MuWire/Configuration" method="post">
			<div class="configuration-section">
				<table>
					<tr>
						<td>Search in comments</td>
						<td><p align="right"><input type="checkbox" <% if (core.getMuOptions().getSearchComments()) out.write("checked"); %> name="searchComments" value="true"></p></td>
					</tr>
					<tr>
						<td>Allow browsing</td>
						<td><p align="right"><input type="checkbox" <% if (core.getMuOptions().getBrowseFiles()) out.write("checked"); %> name="browseFiles" value="true"></p></td>
					</tr>
				</table>
			</div>
			<div class="configuration-section">
				<table>
					<tr>
						<td>Retry failed downloads every (seconds)</td>
						<td><p align="right"><input type="text" size="1" name="downloadRetryInterval" value="<%= core.getMuOptions().getDownloadRetryInterval()%>"></p></td>
					</tr>
					<tr>
						<td>Save downloaded files to</td>
						<td><p align="right"><input type="text" size="30" name="downloadLocation" value="<%= core.getMuOptions().getDownloadLocation().getAbsoluteFile()%>"></p></td>
					</tr>
					<tr> 
						<td>Store incomplete files in</td>
						<td><p align="right"><input type="text" size="30" name="incompleteLocation" value="<%= core.getMuOptions().getIncompleteLocation().getAbsoluteFile()%>"></p></td>
					</tr>
				</table>
			</div>
			<div class="configuration-section">
				<table>
					<tr>
						<td>Total upload slots (-1 means unlimited)</td>
						<td><p align="right"><input type="text" size="1" name="totalUploadSlots" value="<%= core.getMuOptions().getTotalUploadSlots() %>"></p></td>
					</tr>
					<tr>
						<td>Upload slots per user (-1 means unlimited)</td>
						<td><p align="right"><input type="text" size="1" name="uploadSlotsPerUser" value="<%= core.getMuOptions().getUploadSlotsPerUser() %>"></p></td>
					</tr>
				</table>
			</div>
			<div class="configuration-section">
				<table>
					<tr>
						<td>Share downloaded files</td>
						<td><p align="right"><input type="checkbox" <% if (core.getMuOptions().getShareDownloadedFiles()) out.write("checked"); %> name="shareDownloadedFiles" value="true"></p></td>
					</tr>
					<tr>
						<td>Share hidden files</td>
						<td><p align="right"><input type="checkbox" <% if (core.getMuOptions().getShareHiddenFiles()) out.write("checked"); %> name="shareHiddenFiles" value="true"></p></td>
					</tr>
				</table>
			</div>
			<div class="configuration-section">
				<table>
					<tr>
						<td>Allow only trusted connections</td>
						<td><p align="right"><input type="checkbox" <% if (!core.getMuOptions().getAllowUntrusted()) out.write("checked"); %> name="allowUntrusted" value="true"></p></td>
					</tr>
					<tr>
						<td>Search extra hop</td>
						<td><p align="right"><input type="checkbox" <% if (core.getMuOptions().getSearchExtraHop()) out.write("checked"); %> name="searchExtraHop" value="true"></p></td>
					</tr>
					<tr>
						<td>Allow others to view my trust list</td>
						<td><p align="right"><input type="checkbox" <% if (core.getMuOptions().getAllowTrustLists()) out.write("checked"); %> name="allowTrustLists" value="true"></p></td>
					</tr>
					<tr>
						<td>Update trust lists every (hours)</td>
						<td><p align="right"><input type="text" size="1" name="trustListInterval" value="<%= core.getMuOptions().getTrustListInterval() %>"</p></td>
					</tr>
				</table>
			</div>
			<input type="submit" value="<%=Util._t("Save")%>">
		</form>
	</section>
    </body>
</html>
