import csv
from shapely.geometry import Point


########################################################
#  
#TODO Docstrings
#
########################################################
class FaultSubSection():
    """
    """

    @staticmethod
    def from_csv_row(row, parent=None):
        """
        row.headers along_strike_index,down_dip_index,lon1(deg),lat1(deg),lon2(deg),lat2(deg),dip (deg),top_depth (km),bottom_depth (km)
        """
        fs = FaultSubSection(parent)

        fs._data = row
        fs._idx = (int(row['along_strike_index']), int(row['down_dip_index']))
        fs._top_trace = [Point(float(row['lat1(deg)']) , float(row['lon1(deg)'])), Point(float(row['lat2(deg)']) , float(row['lon2(deg)']))]
        fs._dip = float(row['dip (deg)'])
        fs._top_depth = float(row['top_depth (km)'])
        fs._bottom_depth = float(row['bottom_depth (km)'])
        return fs

    def __init__(self, parent=None):
        self._parent = parent
        self._data = None
        self._top_trace = None

    def __repr__(self):
        return "%s :: %s" % (self.__class__.__name__, self._top_trace)

    @property
    def parent(self):
        return self._parent 

    @property
    def strike_dip_index(self):
        return self._idx

    @property
    def top_trace(self):
        return self._top_trace

    @property
    def dip(self):
        return self._dip 

    @property
    def top_depth(self):
        return self._top_depth  

    @property
    def bottom_depth(self):
        return self._bottom_depth 


class SheetFault():
    def __init__(self, name):
        self._name = name
        self._sub_sections = []
        self._ruptures = []

    def  build_surface_from_csv(self, csv_data):
        reader = csv.DictReader(csv_data)

        #validate
        rows = [x for x in reader]
        if not rows[0]['lat1(deg)']:
            raise ValueError('Invalid header')

        for row in rows:
            self._sub_sections.append(FaultSubSection.from_csv_row(row, parent=self))

        return self

    @property
    def name(self):
        return self._name 

    @property
    def sub_sections(self):
        return self._sub_sections

    def get_ruptures(self, min_size = 1):
        return self._ruptures 



