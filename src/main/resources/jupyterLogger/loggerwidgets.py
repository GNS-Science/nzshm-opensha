import geopandas
from ipyleaflet import Map, GeoData, LegendControl, FullScreenControl, ScaleControl, WidgetControl, LayersControl
from ipywidgets import HTML

palette = ['#d73027', '#4575b4', '#f46d43', '#74add1', '#fdae61', '#abd9e9', '#fee090', '#e0f3f8']


class LogMap(Map):
    def __init__(self, center=[-41.5, 175.0], zoom=5):
        Map.__init__(self, center=center, zoom=zoom)
        self.section_info = HTML()
        self.section_info.value = "Hover over features for more details."
        widget_control = WidgetControl(widget=self.section_info, position='topright')
        self.add(widget_control)
        self.legend_control = LegendControl({}, title='')
        self.add(FullScreenControl(position='topleft'))
        self.add(self.legend_control)
        self.add(LayersControl(position='topright'))
        self.add(ScaleControl(position='bottomleft', metric=True, imperial=False))

    def hover_callback(self, event, **kwargs):
        self.section_info.value = "<ul>"
        for k, v in kwargs["properties"].items():
            if k != 'style':
                self.section_info.value += ("<li> " + k + ": " + str(v) + "</li>")
        self.section_info.value += "</ul>"

    def add_layer(self, name, data, colour=None):
        if isinstance(data, str):
            df = geopandas.read_file(data)
        else:
            df = data
        clean_colour = colour if colour is not None else palette[len(self.layers) % len(palette)]
        layer = GeoData(name=name,
                        geo_dataframe=df,
                        style={'color': clean_colour, 'weight': 4},
                        point_style={'color': clean_colour, 'weight': 4},
                        hover_style={'color': 'white', 'weight': 4})
        layer.on_hover(self.hover_callback)
        layer.on_click(self.hover_callback)
        self.add(layer)
        self.legend_control.add_legend_element(name, clean_colour)
        return (layer, df)
