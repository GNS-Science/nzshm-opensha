"""
Utility script

read in Fault Mdeol fro Russ Van Dissen and obtains differences compared to cfm_0_9c_d90_all.xml

now create cfm_0_9d_* using the modified aveLowerDepths from the above diff.

NB we must match on the SectionName attribute as the actual fault sections IDs differ.

"""

from lxml import etree
import xmldiff.diff
import copy

def modtree(fault_section_depths, tree):
    t2 = copy.deepcopy(tree)
    for sectionName, newDepth in fault_section_depths.items():
        #print(itm[0])
        #sectionName = tree.xpath(itm)[0].get('sectionName')
        elem = t2.xpath(f'//*[@sectionName="{sectionName}"]')
        if elem: 
            e = elem[0]
            old = e.get("aveLowerDepth")
            e.set("aveLowerDepth", newDepth)
            print(f'{sectionName} was {old} => {e.get("aveLowerDepth")}')
    return t2


if __name__ == '__main__':
    _src = "cfm_0_9c_snasTVZ_d90_TVZ_0.66Dfc.xml"
    _all = "cfm_0_9c_d90_all.xml"
    _no_tvz = "cfm_0_9c_d90_no_tvz.xml"

    #get the sections to modify
    left = etree.parse(open(_all))
    right = etree.parse(open(_src))

    differ = xmldiff.diff.Differ()
    fault_section_depths = {}
    for d in differ.diff(left, right):
        elem = right.xpath(d.node)[0]
        fault_section_depths[elem.get('sectionName')] = d.value

    #create copy and write out the modified trees
    for f in [_all, _no_tvz]:
        t0 = etree.parse(open(f))
        t1 = modtree(fault_section_depths, t0)
        fout = f.replace("0_9c", "0_9d")
        print(f, fout)
        print('=' * 20)
        with open(fout, 'wb') as ff:
            t1.write(ff, encoding="utf-8", xml_declaration=True, pretty_print=True)
        print()