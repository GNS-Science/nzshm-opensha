import csv
from shapely.geometry import Point
import numpy as np


class FaultSubSectionFactory():

    def __init__(self):
        self.current_idx = 0

    def new_fault_sub_section(self, parent):
        fss = FaultSubSection(self.current_idx, parent)
        self.current_idx += 1
        return fss


# TODO Docstrings
class FaultSubSection():
    """
    """

    @staticmethod
    def from_csv_row(factory, row, parent=None):
        """
        row.headers along_strike_index,down_dip_index,lon1(deg),lat1(deg),
        lon2(deg),lat2(deg),dip (deg),top_depth (km),bottom_depth (km)
        """
        fs = factory.new_fault_sub_section(parent)

        fs._data = row
        fs._idx = (int(row['along_strike_index']), int(row['down_dip_index']))
        fs._top_trace = [
            Point(float(row['lat1(deg)']), float(row['lon1(deg)'])),
            Point(float(row['lat2(deg)']), float(row['lon2(deg)']))]
        fs._dip = float(row['dip (deg)'])
        fs._top_depth = float(row['top_depth (km)'])
        fs._bottom_depth = float(row['bottom_depth (km)'])

        return fs

    def __init__(self, id, parent=None):
        self._id = id
        self._parent = parent
        self._data = None
        self._top_trace = None

    def __repr__(self):
        return "%s :: %s" % (self.__class__.__name__, self._top_trace)

    @property
    def id(self):
        return self._id

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
        self._sub_sections = {}
        self._column_max = 0
        self._row_max = 0
        # re-arrange the subsections as per their strike-dip_indices
        self._grid = np.empty((20, 20))
        self._grid[:] = np.nan
        self._ruptures = []

    def build_surface_from_csv(self, factory, csv_data):
        reader = csv.DictReader(csv_data)

        # validate
        rows = [x for x in reader]
        if not rows[0]['lat1(deg)']:
            raise ValueError('Invalid header')

        for row in rows:
            ss = FaultSubSection.from_csv_row(factory, row, parent=self)
            self._sub_sections[ss.id] = ss
            idx = ss.strike_dip_index
            self._column_max = max(self._column_max, idx[0])
            self._row_max = max(self._row_max, idx[1])
            self._grid[idx[0]][idx[1]] = ss.id
        return self

    @property
    def name(self):
        return self._name

    @property
    def sub_sections(self):
        return self._sub_sections

    def get_ruptures(self, spec):

        # name = spec['name']
        scale = spec['scale']
        aspect = spec['aspect']

        min_fill_factor = spec.get('min_fill_factor', 0.75)
        min_sections = int(scale * scale * aspect) * min_fill_factor

        def tuples_for_rupture_ids(ids):
            res = []
            for id in ids:
                res.append(self.sub_sections[id].strike_dip_index)
            return res

        for col in range(0, self._column_max + 1):
            for row in range(0, self._row_max + 1):
                # get ruptures by range
                rupt = self._grid[col:col+int(scale*aspect), row:row+scale]
                # remove empty values
                rupt = rupt[np.logical_not(np.isnan(rupt))]
                # convert to integerrs and get a flat list
                rupt = rupt.astype(int).flatten().tolist()
                if len(rupt) and len(rupt) > min_sections:
                    print(tuples_for_rupture_ids(rupt))
                    yield tuples_for_rupture_ids(rupt)
