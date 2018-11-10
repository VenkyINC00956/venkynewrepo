package oneapp.incture.workbox.sharepoint.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.olingo.odata2.api.commons.HttpStatusCodes;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.edm.EdmEntityContainer;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.apache.olingo.odata2.api.ep.EntityProviderReadProperties;
import org.apache.olingo.odata2.api.ep.entry.ODataEntry;
import org.apache.olingo.odata2.api.ep.feed.ODataFeed;
import org.apache.olingo.odata2.api.exception.ODataException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

//import com.incture.pmc.workbox.dto.ActionsRequestDto;

import oneapp.incture.workbox.inbox.dto.WorkboxRequestDto;
import oneapp.incture.workbox.poadapter.dto.ResponseMessage;
import oneapp.incture.workbox.util.PMCConstant;
import oneapp.incture.workbox.util.ServicesUtil;

/**
 * Contains utility functions to be used for consuming oData Services
 * 
 * @version R1
 */@Component
public class ODataServicesUtil {

	public static String USERNAME;
	public static String PASSWORD;

	public static ODataEntry readEntry(Edm edm, String serviceUri, String contentType, String entitySetName,
			String keyValue) throws IOException, ODataException {
		return readEntry(edm, serviceUri, contentType, entitySetName, keyValue, null);
	}

	public static ODataEntry readEntry(Edm edm, String serviceUri, String contentType, String entitySetName,
			String keyValue, String expandRelationName) throws IOException, ODataException {
		EdmEntityContainer entityContainer = edm.getDefaultEntityContainer();
		String absolutUri = createUri(serviceUri, entitySetName, keyValue, expandRelationName);

		InputStream content = execute(absolutUri, contentType, PMCConstant.HTTP_METHOD_GET);

		return EntityProvider.readEntry(contentType, entityContainer.getEntitySet(entitySetName), content,
				EntityProviderReadProperties.init().build());
	}

	private static String createUri(String serviceUri, String entitySetName, String id) {
		return createUri(serviceUri, entitySetName, id, null);
	}

	private static String createUri(String serviceUri, String entitySetName, String id, String expand) {
		final StringBuilder absolutUri = new StringBuilder(serviceUri).append(PMCConstant.SEPARATOR)
				.append(entitySetName);
		if (id != null) {
			absolutUri.append("(").append(id).append(")");
		}
		if (expand != null) {
			absolutUri.append("/?$expand=").append(expand);
		}
		return absolutUri.toString();
	}

	public static InputStream execute(String relativeUri, String contentType, String httpMethod) throws IOException {
		InputStream content = null;
		HttpURLConnection connection = null;
		try {
			connection = initializeConnection(relativeUri, contentType, httpMethod);
			connection.connect();
			checkStatus(connection);
			// String csfr = connection.getHeaderField("x-csrf-token");
			// System.err.println("[PMC][ODataServicesUtil][execute][csfr]
			// "+csfr);
			content = connection.getInputStream();
			content = logRawContent(httpMethod + " request on uri '" + relativeUri + "' with content:\n  ", content,
					"\n");

		} catch (Exception e) {
			System.err.println("[PMC][ODataServicesUtil][execute][error]  " + e.getMessage());
		} finally {
			if (!ServicesUtil.isEmpty(connection)) {
				connection.disconnect();
			}
		}
		return content;
	}

	private static HttpURLConnection initializeConnection(String absolutUri, String contentType, String httpMethod)
			throws MalformedURLException, IOException {
		URL url = new URL(absolutUri);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		connection.setRequestMethod(httpMethod);
		connection.setRequestProperty(PMCConstant.HTTP_HEADER_ACCEPT, contentType);
		// String userpass = USERNAME + ":" + PASSWORD ;
		// String basicAuth = "Basic " +
		// javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());
		connection.setRequestProperty("Authorization", getBasicAuth());

		if (PMCConstant.HTTP_METHOD_POST.equals(httpMethod) || PMCConstant.HTTP_METHOD_PUT.equals(httpMethod)) {
			connection.setDoOutput(true);
			connection.setRequestProperty(PMCConstant.HTTP_HEADER_CONTENT_TYPE, contentType);
		}

		return connection;
	}

	private static HttpStatusCodes checkStatus(HttpURLConnection connection) throws IOException {
		HttpStatusCodes httpStatusCode = HttpStatusCodes.fromStatusCode(connection.getResponseCode());
		if (400 <= httpStatusCode.getStatusCode() && httpStatusCode.getStatusCode() <= 599) {
			throw new RuntimeException("Http Connection failed with status " + httpStatusCode.getStatusCode() + " "
					+ httpStatusCode.toString());
		}
		return httpStatusCode;
	}

	private static InputStream logRawContent(String prefix, InputStream content, String postfix) throws IOException {
		if (PMCConstant.PRINT_RAW_CONTENT) {
			byte[] buffer = streamToArray(content);
			return new ByteArrayInputStream(buffer);
		}
		return content;
	}

	private static byte[] streamToArray(InputStream stream) throws IOException {
		byte[] result = new byte[0];
		byte[] tmp = new byte[8192];
		int readCount = stream.read(tmp);
		while (readCount >= 0) {
			byte[] innerTmp = new byte[result.length + readCount];
			System.arraycopy(result, 0, innerTmp, 0, result.length);
			System.arraycopy(tmp, 0, innerTmp, result.length, readCount);
			result = innerTmp;
			readCount = stream.read(tmp);
		}
		stream.close();
		return result;
	}

	public static Edm readEdm(String serviceUrl, String username, String password) throws IOException, ODataException {
		if (!ServicesUtil.isEmpty(username) && !ServicesUtil.isEmpty(password)) {
			USERNAME = username;
			PASSWORD = ServicesUtil.getDecryptedText(password);
		}
		InputStream content = execute(serviceUrl + PMCConstant.SEPARATOR + PMCConstant.METADATA,
				PMCConstant.APPLICATION_XML, PMCConstant.HTTP_METHOD_GET);
		return EntityProvider.readMetadata(content, false);
	}

	public static ODataFeed readFeed(Edm edm, String serviceUri, String contentType, String entitySetName)
			throws IOException, ODataException {
		EdmEntityContainer entityContainer = edm.getDefaultEntityContainer();
		String absolutUri = createUri(serviceUri, entitySetName, null);

		InputStream content = execute(absolutUri, contentType, PMCConstant.HTTP_METHOD_GET);
		return EntityProvider.readFeed(contentType, entityContainer.getEntitySet(entitySetName), content,
				EntityProviderReadProperties.init().build());
	}

	public static String readActions(String serviceUri, String contentType) throws IOException, ODataException {
		HttpURLConnection connection = null;
		String actions = "";
		try {
			connection = initializeConnection(serviceUri, contentType, PMCConstant.HTTP_METHOD_GET);

			connection.connect();
			checkStatus(connection);

			InputStream content = connection.getInputStream();
			byte[] buffer = streamToArray(content);
			String st = new String(buffer);
			// System.err.println("[PMC][ODataServicesUtil][readActions][st] "+
			// st);
			if (st.contains("Submit")) {
				actions = actions + "Submit";
			}
			if (st.contains("Approve")) {
				if (!ServicesUtil.isEmpty(actions)) {
					actions = actions + ",";
				}
				actions = actions + "Approve";
			}
			if (st.contains("Reject")) {
				if (!ServicesUtil.isEmpty(actions)) {
					actions = actions + ",";
				}
				actions = actions + "Reject";
			}
		} catch (Exception e) {
			System.err.println("[PMC][ODataServicesUtil][readActions][error]  " + e.getMessage());
		} finally {
			if (!ServicesUtil.isEmpty(connection)) {
				connection.disconnect();
			}
		}
		// System.err.println("[PMC][ODataServicesUtil][readActions][result] "+
		// actions);
		return actions;
	}

	/*
	 * public static String getCountInString(String sapOrigin, String
	 * instanceId,String type){ String serviceUri =
	 * "http://sthcigwdq1.kaust.edu.sa:8005/sap/opu/odata/IWPGW/TASKPROCESSING;mo;v=2/TaskCollection(SAP__Origin='"
	 * +sapOrigin+"',InstanceID='"+instanceId+"')/"+type+"/$count";
	 * HttpURLConnection connection = null; String st= ""; try{ connection =
	 * initializeConnection(serviceUri, PMCConstant.APPLICATION_XML,
	 * PMCConstant.HTTP_METHOD_GET); connection.connect();
	 * checkStatus(connection); InputStream content =
	 * connection.getInputStream(); byte[] buffer = streamToArray(content); st =
	 * new String(buffer);
	 * 
	 * }catch(Exception e){
	 * System.err.println("[PMC][ODataServicesUtil][getCountInString][error]  "+
	 * e.getMessage()); } finally{ if(!ServicesUtil.isEmpty(connection)){
	 * connection.disconnect(); } } return st; }
	 */

	// public static String getAttachment(String instanceId,String
	// sapOrigin,String username ,String scode, String id){
	// // String serviceUri =
	// "http://sthcigwdq1.kaust.edu.sa:8005/sap/opu/odata/IWPGW/TASKPROCESSING;mo;v=2/TaskCollection(SAP__Origin='"+sapOrigin+"',InstanceID='"+instanceId+"')/"+type+"/$count";
	// System.err.println("[PMC][ODataServicesUtil][Xpath][getAttachment]initiated
	// ");
	// if(!ServicesUtil.isEmpty(username) && !ServicesUtil.isEmpty(scode))
	// {
	// if(!ServicesUtil.isEmpty(sapOrigin) &&
	// !ServicesUtil.isEmpty(instanceId)&& !ServicesUtil.isEmpty(id)){
	// USERNAME = username;
	// PASSWORD = ServicesUtil.getDecryptedText(scode);
	// String serviceUri =
	// "http://sthcigwdq1.kaust.edu.sa:8005/sap/opu/odata/IWPGW/TASKPROCESSING;mo;v=2/AttachmentCollection(SAP__Origin='"+sapOrigin+"',InstanceID='"+instanceId+"',ID='"+id+"')/$value";
	//
	// HttpURLConnection connection = null;
	// String st= "";
	// try{
	// connection = initializeConnection(serviceUri,
	// PMCConstant.APPLICATION_XML, PMCConstant.HTTP_METHOD_GET);
	// connection.connect();
	// checkStatus(connection);
	// InputStream content = connection.getInputStream();
	// byte[] buffer = streamToArray(content);
	// st = new String(buffer);
	//
	// }catch(Exception e){
	// System.err.println("[PMC][ODataServicesUtil][getAttachment][error] "+
	// e.getMessage());
	// }
	// finally{
	// if(!ServicesUtil.isEmpty(connection)){
	// connection.disconnect();
	// }
	// }
	//
	// System.err.println("[PMC][ODataServicesUtil][Xpath][getAttachment]exited
	// with " +st);
	// return st;
	// }
	// else{
	// return "";
	// }
	// }
	// else {
	// return "";
	// }
	// }

	public static InputStream getAttachment(String instanceId, String sapOrigin, String username, String scode,
			String id) {
		// String serviceUri =
		// "http://sthcigwdq1.kaust.edu.sa:8005/sap/opu/odata/IWPGW/TASKPROCESSING;mo;v=2/TaskCollection(SAP__Origin='"+sapOrigin+"',InstanceID='"+instanceId+"')/"+type+"/$count";
		System.err.println("[PMC][ODataServicesUtil][Xpath][getAttachment]initiated ");
		if (!ServicesUtil.isEmpty(username) && !ServicesUtil.isEmpty(scode)) {
			if (!ServicesUtil.isEmpty(sapOrigin) && !ServicesUtil.isEmpty(instanceId) && !ServicesUtil.isEmpty(id)) {
				USERNAME = username;
				PASSWORD = ServicesUtil.getDecryptedText(scode);
				String serviceUri = "http://sthcigwdq1.kaust.edu.sa:8005/sap/opu/odata/IWPGW/TASKPROCESSING;mo;v=2/AttachmentCollection(SAP__Origin='"
						+ sapOrigin + "',InstanceID='" + instanceId + "',ID='" + id + "')/$value";

				// HttpURLConnection connection = null;
				InputStream inputStream = null;
				CloseableHttpClient httpclient = HttpClients.createDefault();
				try {
					HttpGet httpGet = new HttpGet(serviceUri);
					httpGet.setHeader("Authorization", getBasicAuth());
					CloseableHttpResponse response_get = httpclient.execute(httpGet);
					HttpEntity entity_get = response_get.getEntity();
					inputStream = entity_get.getContent();

				} catch (Exception e) {
					System.err.println("[PMC][ODataServicesUtil][getAttachment][error]  " + e.getMessage());
				} finally {
					/*
					 * if(!ServicesUtil.isEmpty(connection)){
					 * connection.disconnect(); }
					 */
				}

				System.err.println("[PMC][ODataServicesUtil][Xpath][getAttachment]exited with  " + inputStream);
				return inputStream;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	public static ResponseMessage executeAction(WorkboxRequestDto requestDto) {
		System.err.println("[PMC][ODataServicesUtil][actions][executeAction][init] " + requestDto);
		ResponseMessage returnMessage = new ResponseMessage();

		returnMessage.setStatus("FAILURE");
		returnMessage.setStatusCode("1");
		System.err.println("[PMC][ODataServicesUtil][Xpath][actions]initiated ");

		// USERNAME = requestDto.getUserId();
		// PASSWORD = ServicesUtil.getDecryptedText(requestDto.getScode());
		String serviceBase = "https://incturet.sharepoint.com/sites/Workbox/_api/lists/getbytitle('Task%20Details%20New')/items";
		try {
			returnMessage = executeActionHttp(serviceBase);
		} catch (IOException e) {
			System.err.println(e.getLocalizedMessage());
			e.printStackTrace();
		}
		System.err.println("[PMC][ODataServicesUtil][Xpath][actions][username]" + USERNAME + "[serviceUri] ");

		return returnMessage;
	}

	public static NodeList xPathOdata(String serviceUrl, String usedFormatXml, String type, String expression,
			String username, String password) {
		if (!ServicesUtil.isEmpty(username) && !ServicesUtil.isEmpty(password)) {
			USERNAME = username;
			PASSWORD = password;// ServicesUtil.getDecryptedText(password);
		}
		NodeList nodeList = null;
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

			InputStream inputFile = execute(serviceUrl, usedFormatXml, type);
			Document doc = dBuilder.parse(inputFile);
			doc.getDocumentElement().normalize();

			XPath xPath = XPathFactory.newInstance().newXPath();

			nodeList = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return nodeList;
	}

	public static ResponseMessage executeActionHttp(String url) throws IOException {
		System.err.println("[PMC][ODataServicesUtil][actions][executeActionHttp][entry] " + url);
		ResponseMessage returnMessage = new ResponseMessage();
		String X_CSRF_TOKEN = "";
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpPost postRequest = new HttpPost(url);
		try {
			{
				HttpGet httpget = new HttpGet(url);
				httpget.setHeader("Authorization", getBasicAuth());
				httpget.setHeader("x-csrf-token", "fetch");
				Header headers[] = httpget.getAllHeaders();
				HttpResponse res = httpclient.execute(httpget);
				headers = res.getAllHeaders();
				for (Header h : headers) {
					// System.out.println(h.getName() + "---:---- " +
					// h.getValue());
					if (h.getName().equals("x-csrf-token")) {
						X_CSRF_TOKEN = h.getValue();
						System.err
								.println("[PMC][ODataServicesUtil][actions][executeActionHttp][csrf] " + X_CSRF_TOKEN);
					}
				}
			}
			// The main POST REQUEST

			System.err.println("[PMC][ODataServicesUtil][actions][executeAction][beforePost] " + X_CSRF_TOKEN);
			postRequest.addHeader("Authorization", getBasicAuth());
			postRequest.setHeader("x-csrf-token", X_CSRF_TOKEN); // PASSING THE
																	// TOKEN
																	// GOTTEN
																	// FROM THE
																	// CODE
																	// ABOVE

			HttpResponse response = httpclient.execute(postRequest);
			String result = EntityUtils.toString(response.getEntity());
			int responseCode = response.getStatusLine().getStatusCode();

			System.err
					.println("Http Response: Response code " + responseCode + "result.toString()" + result.toString());
			returnMessage.setStatusCode(Integer.toString(responseCode));
			if (responseCode != 200) {
				returnMessage.setStatus("FAILURE");
			} else {
				returnMessage.setStatus("SUCCESS");
			}

			if (responseCode != 200 && result.toString().contains("error")) {
				try {
					DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
					DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
					Document doc;
					InputStream stream = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8.name()));
					doc = dBuilder.parse(stream);
					doc.getDocumentElement().normalize();
					XPath xPath = XPathFactory.newInstance().newXPath();
					XPathExpression expr1 = xPath.compile("error/message/text()");
					NodeList node1 = (NodeList) expr1.evaluate(doc, XPathConstants.NODESET);
					returnMessage.setMessage(node1.item(0).getNodeValue());

				} catch (Exception e1) {
					System.err.println(
							"[PMC][ODataServicesUtil][actions][executeActionHttp][togetError]" + e1.getMessage());
				}
			}

		} catch (Exception e) {
			System.err.println("[PMC][ODataServicesUtil][actions][executeActionHttp][error]" + e.getMessage());
			returnMessage.setMessage(e.getMessage());
			returnMessage.setStatus("FAILURE");
			returnMessage.setStatusCode("1");
		}
		System.err.println("[PMC][ODataServicesUtil][actions][executeActionHttp][exit]" + returnMessage);
		return returnMessage;
	}

	private static String getBasicAuth() {
		// String userpass = userName + ":" + password;
		String userpass = USERNAME + ":" + PASSWORD;
		return "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());
	}

}