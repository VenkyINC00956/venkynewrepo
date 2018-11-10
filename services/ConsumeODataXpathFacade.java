package oneapp.incture.workbox.sharepoint.services;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

//import javax.ejb.EJB;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import oneapp.incture.workbox.poadapter.dao.ProcessEventsDao;
import oneapp.incture.workbox.poadapter.dao.TaskEventsDao;
import oneapp.incture.workbox.poadapter.dao.TaskOwnersDao;
import oneapp.incture.workbox.poadapter.dto.ProcessEventsDto;
import oneapp.incture.workbox.poadapter.dto.ResponseMessage;
import oneapp.incture.workbox.poadapter.dto.TaskEventsDto;
import oneapp.incture.workbox.poadapter.dto.TaskOwnersDto;
import oneapp.incture.workbox.util.PMCConstant;
import oneapp.incture.workbox.util.ServicesUtil;

/**
 * Session Bean implementation class ConsumeODataFacade
 */
@Service("ConsumeODataXpathFacade")
public class ConsumeODataXpathFacade implements ConsumeODataXpathFacadeLocal {

	@Autowired
	ProcessEventsDao processEventsDao;

	@Autowired
	TaskOwnersDao taskOwnerDao;

	@Autowired
	TaskEventsDao taskEventsDao;

	public ConsumeODataXpathFacade() {
	}

	@Override
	public ResponseMessage getDataFromECCNew(String processor, String scode) {
		ResponseMessage responseMessage = new ResponseMessage();
		responseMessage.setMessage("Data Consumed Successfully");
		responseMessage.setStatus("SUCCESS");
		responseMessage.setStatusCode("0");
		try {
			// final long startTime = System.nanoTime();
			NodeList nodeList = ODataServicesUtil.xPathOdata(
					"https://incturet.sharepoint.com/sites/Workbox/_api/lists/getbytitle('Task%20Details%20New')/items",
					PMCConstant.APPLICATION_ATOM_XML, PMCConstant.HTTP_METHOD_GET, "/feed /entry ", processor, scode);
			if (!ServicesUtil.isEmpty(nodeList)) {
				if (nodeList.getLength() > 0) {
					List<String> instanceList = new ArrayList<String>();
					int i;
					for (i = 0; i < nodeList.getLength(); i++) {
						Node nNode = nodeList.item(i);
						if (nNode.getNodeType() == Node.ELEMENT_NODE) {
							String returnedValue = convertToDto(nNode, processor, scode);
							if (returnedValue.equals("FAILURE")) {
								responseMessage.setMessage("Data consumption failed");
								responseMessage.setStatus("FAILURE");
								responseMessage.setStatusCode("1");
								break;
							} else if (!returnedValue.equals("FAILURE") && !returnedValue.equals("SUCCESS")) {
								instanceList.add(returnedValue);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[PMC][ConsumeODataFacade][Xpath][getDataFromECC][error] " + e.getMessage());
			e.printStackTrace();
			responseMessage.setMessage("Data Consumption failed because - " + e.getMessage());
			responseMessage.setStatus("FAILURE");
			responseMessage.setStatusCode("1");
		}

		return responseMessage;
	}

	private String convertToDto(Node nNode, String processor, String scode) {
		Element eElement = (Element) nNode;
		Element mproperties = (Element) eElement.getElementsByTagName("m:properties").item(0);

		TaskEventsDto taskDto = new TaskEventsDto();
		TaskOwnersDto ownersDto = new TaskOwnersDto();
		ProcessEventsDto processDto = new ProcessEventsDto();
		
		String processId = UUID.randomUUID().toString().replaceAll("-", "");
		String eventId = mproperties.getElementsByTagName("d:Id").item(0).getTextContent();
		String processName = mproperties.getElementsByTagName("d:Task_x0020_Name").item(0).getTextContent();
		String status = mproperties.getElementsByTagName("d:Status").item(0).getTextContent();

		taskDto.setEventId(eventId);
		taskDto.setOrigin("SharePoint");
		ownersDto.setEventId(eventId);
		taskDto.setProcessId(processName);
		taskDto.setStatus(status);
		taskDto.setCurrentProcessor(mproperties.getElementsByTagName("d:Assigned_x0020_ToId").item(0).getTextContent());
		processDto.setProcessId(processId);
		processDto.setStartedAt(dateParser(mproperties, "d:Created"));
		taskDto.setCreatedAt(dateParser(mproperties, "d:Created"));
		taskDto.setCompletedAt(dateParser(mproperties, "d:End_x0020_Date"));
		processDto.setCompletedAt(dateParser(mproperties, "d:End_x0020_Date"));
		taskDto.setCurrentProcessorDisplayName(
				mproperties.getElementsByTagName("d:Assigned_x0020_ToStringId").item(0).getTextContent());
		processDto.setStartedByDisplayName(mproperties.getElementsByTagName("d:AuthorId").item(0).getTextContent());
		processDto.setStartedBy(mproperties.getElementsByTagName("d:AuthorId").item(0).getTextContent());
		processDto.setName(mproperties.getElementsByTagName("d:Task_x0020_Name").item(0).getTextContent());
		taskDto.setProcessName(mproperties.getElementsByTagName("d:Task_x0020_Name").item(0).getTextContent());

		if (!(status.equals("COMPLETED"))) {
			processDto.setStatus("INPROGRESS");
		} else {
			processDto.setStatus(status);
		}
		taskDto.setStatus(status);

		ownersDto.setTaskOwnerDisplayName(
				mproperties.getElementsByTagName("d:Assigned_x0020_ToStringId").item(0).getTextContent());
		ownersDto
				.setTaskOwner(mproperties.getElementsByTagName("d:Assigned_x0020_ToStringId").item(0).getTextContent());

		try {
			if (!ServicesUtil.isEmpty(taskDto))
				taskEventsDao.create(taskDto);
			if (!ServicesUtil.isEmpty(processDto))
				processEventsDao.create(processDto);
			if (!ServicesUtil.isEmpty(ownersDto))
				taskOwnerDao.create(ownersDto);

		} catch (Exception e) {
			System.err.println("[SharePoint][]ODATA error" + e.getMessage());
			e.printStackTrace();
		}

		return PMCConstant.SUCCESS;
	}

	private Date dateParser(Element mproperties, String key) {

		if (!ServicesUtil.isEmpty(mproperties.getElementsByTagName(key)))
			return ServicesUtil.resultTAsDate(mproperties.getElementsByTagName(key).item(0).getTextContent());
		return null;
	}

}
