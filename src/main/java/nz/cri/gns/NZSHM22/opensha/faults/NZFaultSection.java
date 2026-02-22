package nz.cri.gns.NZSHM22.opensha.faults;

import org.dom4j.Element;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

public class NZFaultSection extends FaultSectionPrefData {

    protected String domainNo;
    protected String domainName;

    public String getDomainNo() {
        return domainNo;
    }

    public void setDomainNo(String domainNo) {
        this.domainNo = domainNo;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public static NZFaultSection fromXMLMetadata(Element el) {
        FaultSectionPrefData data = FaultSectionPrefData.fromXMLMetadata(el);
        NZFaultSection section = new NZFaultSection();
        section.setFaultSectionPrefData(data);
        section.domainNo = el.attributeValue("domainNo");
        section.domainName = el.attributeValue("domainName");
        return section;
    }
}
