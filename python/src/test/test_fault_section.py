import unittest
from io import StringIO
from fault_section import SheetFault, FaultSubSection, FaultSubSectionFactory
import csv

# fixtures
# noqa
tile_param_csv = """along_strike_index,down_dip_index,lon1(deg),lat1(deg),lon2(deg),lat2(deg),dip (deg),top_depth (km),bottom_depth (km)
0,0,172.55049384268952,-43.66066194689264,172.44173203440846,-43.70398277095125,17.190332526361505,27.777180834317445,30.732649458700852
0,1,172.47296534458786,-43.6059065011278,172.35583940675505,-43.63534622846773,9.730595837337948,26.05032447851844,27.740481671477475
0,2,172.3391136381901,-43.53988717373262,172.4293503838267,-43.6015505895119,3.354830653187863,25.828426371737926,26.413620262173023
0,3,172.33001404532172,-43.520284730458144,172.28135753916627,-43.60307961223907,6.915901943781332,26.109450377717053,27.313574031156808
0,4,172.24268881162706,-43.47309899751564,172.17280352691546,-43.54739397531888,10.687797287418245,27.331460311201145,29.186033678675194
0,5,172.15289485360287,-43.42000512957658,172.07440468929877,-43.48955601373996,12.304833921583548,29.237078193834066,31.3682063613206
0,6,172.06300915691102,-43.36651675667496,171.97739225607305,-43.43139615156615,11.705865896797393,31.356078313909183,33.38495377744904
1,0,172.64501897152118,-43.59954874059301,172.53382327497368,-43.639313969118454,16.25555174108543,26.795272837432112,29.5944932160583
1,1,172.5637722710231,-43.543754598669,172.44640809376534,-43.572430195437214,8.880200993106621,25.34360676778234,26.887296565515864
1,2,172.44220596867652,-43.477837653859055,172.51851406753119,-43.5487115943056,2.905294291172359,25.318439393304615,25.82529163668574
1,3,172.42154835679807,-43.46135549825015,172.3664181339217,-43.54195812144478,7.037773144797519,25.69814411810224,26.923380803625093
1,4,172.33277139382653,-43.4119492515352,172.2610776847743,-43.48528999219563,10.847676605186154,26.94588539932208,28.82787164265644
1,5,172.242692657873,-43.35738849281896,172.16494794940513,-43.4273373346978,12.61516331423042,28.893903935266238,31.077919034165205
1,6,172.15302850394602,-43.30235827895629,172.0707126372049,-43.369417424178536,12.192769409770285,31.15628950326498,33.268303975531644
1,7,172.06310241360606,-43.25052114911484,171.97193373467607,-43.311105498077104,9.619402294399764,33.35437895826026,35.02540524676675
1,8,171.9677832362838,-43.20879502529762,171.85744119000216,-43.248754914020964,6.463749772675945,34.953611461391944,36.07935718466277
1,9,171.8706056648584,-43.1588406763909,171.75280548116186,-43.18478062962037,8.163253506464207,35.794783448666585,37.214724589681175
1,10,171.78514584969986,-43.09006233469788,171.67422519796153,-43.128820064269775,15.553386466049785,36.85596818020717,39.53732959531796
1,11,171.70018636063736,-43.02293702113887,171.59774188193896,-43.07254995279954,27.061683967322573,39.26701654116051,43.81651137795057
1,12,171.6216914119655,-42.96704242849143,171.52273163875773,-43.020235158453694,36.49785918086159,43.880510447093855,49.82843795462334
2,0,172.73940645104628,-43.53756452937407,172.62426912606702,-43.57066107147479,14.928246050752739,26.134533475945258,28.71062519928138
2,1,172.66171469659787,-43.484309065661236,172.54028415216268,-43.50139779519755,7.976508895510293,24.829958113064578,26.217628935260592
2,2,172.5480471021392,-43.41734130837748,172.60257122940806,-43.498150093509956,3.5127525175150494,24.82039956085725,25.433106523808725
2,3,172.50885961053964,-43.39625111045,172.4570818809257,-43.478006540612846,7.576237730260184,25.25189169403127,26.570344616308496
"""


class TestSubductionZoneFault(unittest.TestCase):

    def setUp(self):
        self.factory = FaultSubSectionFactory()

    def test_create_new_fault(self):
        sf = SheetFault("My First Subduction Zone")
        self.assertEqual(sf.name, "My First Subduction Zone")
        self.assertEqual(len(sf.sub_sections), 0)

    def test_load_sub_sections_from_csv(self):
        sf = SheetFault("9 part Subduction Zone")\
                .build_surface_from_csv(self.factory, StringIO(tile_param_csv))
        self.assertEqual(len(sf.sub_sections), 24)
        self.assertIsInstance(sf.sub_sections[0], FaultSubSection)

        # print(sf.sub_sections.values[-1])
        self.assertIs(sf, sf.sub_sections[0].parent)

    def test_load_sub_sections_from_invalid_csv_exception(self):
        with self.assertRaises((ValueError, IndexError)):
            SheetFault("24 part Subduction Zone")\
                .build_surface_from_csv(self.factory,
                                        StringIO('Sorry this is not csv_data'))


class TestFaultSubSection(unittest.TestCase):

    def setUp(self):
        self.factory = FaultSubSectionFactory()

        reader = csv.DictReader(StringIO(tile_param_csv))
        self.csvrows = [x for x in reader]

    def test_create_from_invalid_csvrow_exception(self):
        with self.assertRaises((KeyError,)):
            FaultSubSection.from_csv_row(self.factory,
                                         dict(x='Sorry this is not csv_data'))

    def test_create_from_csv_row(self):
        fss = FaultSubSection\
                .from_csv_row(self.factory, self.csvrows[0], parent=None)

        self.assertEqual(0, fss.id)
        self.assertAlmostEqual(-43.6606619468, fss.top_trace[0].x)
        self.assertAlmostEqual(172.550493842, fss.top_trace[0].y)

        self.assertEqual((0, 0), fss.strike_dip_index)
        self.assertAlmostEqual(17.190332526, fss.dip)

        self.assertAlmostEqual(27.77718083, fss.top_depth)
        self.assertAlmostEqual(30.73264945, fss.bottom_depth)


class TestGenerateRectangularRuptures(unittest.TestCase):

    def setUp(self):
        self.factory = FaultSubSectionFactory()
        self.sf = SheetFault("9 part Subduction Zone")\
            .build_surface_from_csv(self.factory, StringIO(tile_param_csv))

    # @unittest.skip("WIP")
    def test_rupture_one_by_one(self):
        shape_spec = dict(name="1 by 1", scale=1, aspect=1)
        ruptures = [r for r in self.sf.get_ruptures(shape_spec)]

        self.assertEqual(ruptures[0], [(0, 0)])
        self.assertEqual(ruptures[1], [(0, 1)])
        self.assertEqual(ruptures[7], [(1, 0)])
        self.assertEqual(ruptures[8], [(1, 1)])

    def test_rupture_one_by_two(self):
        shape_spec = dict(name="1 by 2", scale=1, aspect=2)
        ruptures = [r for r in self.sf.get_ruptures(shape_spec)]

        self.assertEqual(ruptures[0], [(0, 0), (1, 0)])     # begin col 0
        self.assertEqual(ruptures[1], [(0, 1), (1, 1)])

        self.assertEqual(ruptures[7], [(1, 0), (2, 0)])     # begin col 1
        self.assertEqual(ruptures[8], [(1, 1), (2, 1)])

    def test_rupture_two_by_one(self):
        shape_spec = dict(name="1 by 2", scale=2, aspect=0.5)
        ruptures = [r for r in self.sf.get_ruptures(shape_spec)]

        self.assertEqual(ruptures[0], [(0, 0), (0, 1)])     # begin col 0
        self.assertEqual(ruptures[5], [(0, 5), (0, 6)])

        self.assertEqual(ruptures[6], [(1, 0), (1, 1)])     # begin col 1
        self.assertEqual(ruptures[17], [(1, 11), (1, 12)])

        self.assertEqual(ruptures[-1], [(2, 2), (2, 3)])    # last

    def test_rupture_two_by_three(self):
        shape_spec = dict(name="2 by 3", scale=2, aspect=1.5)
        ruptures = [r for r in self.sf.get_ruptures(shape_spec)]

        self.assertEqual(ruptures[0], [(0, 0), (0, 1), (1, 0),
                                       (1, 1), (2, 0), (2, 1)])  # col 0

    def test_rupture_four_by_four(self):
        shape_spec = dict(name="4x4", scale=4, aspect=1, min_fill_factor=0.9)
        ruptures = [r for r in self.sf.get_ruptures(shape_spec)]

        self.assertEqual(ruptures[0], [
            (0, 0), (0, 1), (0, 2), (0, 3),
            (1, 0), (1, 1), (1, 2), (1, 3),
            (2, 0), (2, 1), (2, 2), (2, 3)])
