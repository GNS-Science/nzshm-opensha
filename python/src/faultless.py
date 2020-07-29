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
    def from_csv_row(row):
        fs = FaultSubSection()
        fs._data = row
        fs._point_1 = Point(float(row['lat1(deg)']) , float(row['lon1(deg)']))
        fs._point_2 = Point(float(row['lat2(deg)']) , float(row['lon2(deg)']))
        return fs

    def __init__(self):
        self._data = None

    def __repr__(self):
        return "%s :: %s" % (self.__class__.__name__, self._point_1)


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
            self._sub_sections.append(FaultSubSection.from_csv_row(row))

        return self

    @property
    def name(self):
        return self._name 

    @property
    def sub_sections(self):
        return self._sub_sections

    def get_ruptures(self, min_size = 1):
        return self._ruptures 

