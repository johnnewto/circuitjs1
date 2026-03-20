package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class SimulationExportCore {

    public interface Diagnostics {
        void log(String message);
    }

    public static final class RunRequest {
        public String circuitPath;
        public String outputPath;
        public String htmlPath;
        public int steps;
        public String format;
    }

    public static final class RunResult {
        public String outputText;
        public boolean world2Format;
        public String htmlReport;
        public RunParameters runParameters;
        public int rowsWritten;
    }

    public static final class RunParameters {
        String circuitPath;
        String outputPath;
        String htmlPath;
        int stepsRequested;
        String outputFormat;
        boolean world2Format;
        double timestep;
        double currentTimeStep;
        String timeUnit;
        boolean mnaMode;
        double equationTableTolerance;
        String lookupMode;
        int convergenceCheckThreshold;
        boolean eqnTableNewtonJacobian;
        boolean autoAdjustTimestep;
        double minTimeStep;
        double maxTimeStep;
        boolean lookupClamp;
        int computedValueCount;
    }

    static final class World2Row {
        final Double time;
        final Double p;
        final Double polr;
        final Double ci;
        final Double ql;
        final Double nr;

        World2Row(Double time, Double p, Double polr, Double ci, Double ql, Double nr) {
            this.time = time;
            this.p = p;
            this.polr = polr;
            this.ci = ci;
            this.ql = ql;
            this.nr = nr;
        }
    }

    private static final NumFmt.Formatter FIXED_1_FMT = NumFmt.forPattern("0.0");
    private static final NumFmt.Formatter FIXED_3_FMT = NumFmt.forPattern("0.000");
    private static final NumFmt.Formatter FIXED_4_FMT = NumFmt.forPattern("0.0000");
    private static final NumFmt.Formatter PARAM_FMT = NumFmt.forPattern("0.###############");

    private SimulationExportCore() {
    }

    public static RunResult run(CirSim sim, RunRequest request, Diagnostics diagnostics) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (sim == null) {
            throw new IllegalArgumentException("sim must not be null");
        }

        Set<String> registered = ComputedValues.getRegisteredComputedNames();
        List<String> keys = new ArrayList<String>(registered != null ? registered : Collections.<String>emptySet());
        Collections.sort(keys);

        boolean world2Format = "world2".equals(request.format);
        boolean tsvFormat = "tsv".equals(request.format);
        List<World2Row> world2Rows = world2Format ? new ArrayList<World2Row>() : Collections.<World2Row>emptyList();
        RunParameters runParameters = collectRunParameters(sim, request, world2Format, keys.size());
        printRunParameters(diagnostics, runParameters);

        StringBuilder output = new StringBuilder();
        appendHeader(output, keys, world2Format, tsvFormat);

        boolean warnedNoTimeAdvance = false;
        int rowsWritten = 0;
        for (int step = 0; step < request.steps; step++) {
            double prevT = sim.t;
            sim.runCircuit(step == 0);
            ComputedValues.commitConvergedValues();

            if (sim.stopMessage != null) {
                log(diagnostics, "CircuitJavaRunner: simulation stopped at step " + step + ": " + sim.stopMessage);
            }
            if (!warnedNoTimeAdvance && sim.t == prevT) {
                warnedNoTimeAdvance = true;
                log(diagnostics, "CircuitJavaRunner: time did not advance at step " + step + " (t=" + sim.t + ")");
            }

            if (world2Format) {
                Double p = ComputedValues.getConvergedValue("P");
                Double polr = ComputedValues.getConvergedValue("POLR");
                Double ci = ComputedValues.getConvergedValue("CI");
                Double ql = ComputedValues.getConvergedValue("QL");
                Double nr = getNaturalResourcesValue();
                appendWorld2Row(output, sim.t, p, polr, ci, ql, nr);
                world2Rows.add(new World2Row(sim.t, p, polr, ci, ql, nr));
            } else {
                appendDelimitedRow(output, sim.t, keys, tsvFormat ? '\t' : ',', tsvFormat);
            }
            rowsWritten++;

            if (sim.stopMessage != null) {
                break;
            }
        }

        RunResult result = new RunResult();
        result.world2Format = world2Format;
        result.outputText = output.toString();
        result.runParameters = runParameters;
        result.rowsWritten = rowsWritten;
        if (world2Format) {
            result.htmlReport = buildWorld2HtmlReport(request.circuitPath, request.steps, world2Rows, runParameters);
        }
        return result;
    }

    private static void appendHeader(StringBuilder out, List<String> keys, boolean world2Format, boolean tsvFormat) {
        if (world2Format) {
            out.append("Year\tPopulation\tPollution Ratio\tCapital Investment\tQuality of Life\tNatural Resources\n");
            return;
        }
        char separator = tsvFormat ? '\t' : ',';
        out.append("t");
        for (String key : keys) {
            out.append(separator).append(key);
        }
        out.append('\n');
    }

    private static void appendDelimitedRow(StringBuilder out, double time, List<String> keys, char separator, boolean formatWithSI) {
        out.append(time);
        for (String key : keys) {
            Double value = ComputedValues.getConvergedValue(key);
            out.append(separator);
            if (value != null) {
                out.append(formatWithSI ? fmtSI(value) : value);
            }
        }
        out.append('\n');
    }

    private static void appendWorld2Row(StringBuilder out, double time, Double p, Double polr, Double ci, Double ql, Double nr) {
        out.append(fmtFixedPrimitive(time, 1));
        out.append('\t');
        out.append(fmtSI(p));
        out.append('\t');
        out.append(fmtFixed(polr, 4));
        out.append('\t');
        out.append(fmtSI(ci));
        out.append('\t');
        out.append(fmtFixed(ql, 4));
        out.append('\t');
        out.append(fmtSI(nr));
        out.append('\n');
    }

    static String buildWorld2HtmlReport(String circuitPath, int steps, List<World2Row> rows, RunParameters runParameters) {
        StringBuilder jsonRows = new StringBuilder();
        jsonRows.append("[");
        for (int i = 0; i < rows.size(); i++) {
            World2Row row = rows.get(i);
            if (i > 0) {
                jsonRows.append(',');
            }
            jsonRows.append("{\"t\":");
            appendJsonNumber(jsonRows, row.time);
            jsonRows.append(",\"P\":");
            appendJsonNumber(jsonRows, row.p);
            jsonRows.append(",\"POLR\":");
            appendJsonNumber(jsonRows, row.polr);
            jsonRows.append(",\"CI\":");
            appendJsonNumber(jsonRows, row.ci);
            jsonRows.append(",\"QL\":");
            appendJsonNumber(jsonRows, row.ql);
            jsonRows.append(",\"NR\":");
            appendJsonNumber(jsonRows, row.nr);
            jsonRows.append("}");
        }
        jsonRows.append("]");

        String title = "World2 Output - " + fileNameOnly(circuitPath);
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        html.append("  <title>").append(escapeHtml(title)).append("</title>\n");
        html.append("  <script src=\"https://cdn.plot.ly/plotly-2.27.0.min.js\"></script>\n");
        html.append("  <style>body{font-family:sans-serif;margin:0;padding:12px;background:#fff;}h2{margin:0 0 8px 0;}.tabs{margin-bottom:8px;}.tabs button{padding:4px 12px;cursor:pointer;background:#eee;border:1px solid #ccc;border-radius:3px 3px 0 0;margin-right:4px;}.tabs button.active{background:#fff;border-bottom-color:#fff;font-weight:bold;}.tab-content{display:none;}.tab-content.active{display:block;}table{border-collapse:collapse;font-size:13px;}th,td{border:1px solid #ccc;padding:3px 10px;text-align:right;white-space:nowrap;}th{background:#f0f0f0;text-align:center;font-weight:600;}td:first-child,th:first-child{text-align:left;}tr:nth-child(even) td{background:#fafafa;}#plots-container{width:80%;max-width:80%;aspect-ratio:5 / 3;height:auto;min-height:0;box-sizing:border-box;margin:12px auto 0 auto;background:#fff;}#plots-title{width:80%;max-width:80%;margin:6px auto 0 auto;font-size:13px;font-weight:600;color:#222;text-align:left;}#status{color:#555;margin-bottom:8px;} .meta{font-size:11px;color:#666;margin-bottom:8px;}</style>\n");
        html.append("</head>\n<body>\n");
        html.append("  <h2>World2 Output</h2>\n");
        html.append("  <div class=\"meta\">Circuit: ").append(escapeHtml(circuitPath)).append(" &mdash; Steps: ").append(steps).append(" &mdash; Rows: ").append(rows.size()).append("</div>\n");
        appendRunParametersHtml(html, runParameters);
        html.append("  <div class=\"tabs\">\n");
        html.append("    <button class=\"active\" onclick=\"showTab('table')\">Output Table</button>\n");
        html.append("    <button onclick=\"showTab('plots')\">Plots</button>\n");
        html.append("  </div>\n");
        html.append("  <div id=\"tab-table\" class=\"tab-content active\">\n");
        html.append("    <div id=\"status\"></div>\n");
        html.append("    <div id=\"table-container\"></div>\n");
        html.append("  </div>\n");
        html.append("  <div id=\"tab-plots\" class=\"tab-content\">\n");
        html.append("    <div style=\"margin:0 0 8px 0; font-size:13px;\">\n");
        html.append("      <label for=\"plot-mode-select\" style=\"margin-right:6px;\">Plot mode:</label>\n");
        html.append("      <select id=\"plot-mode-select\" onchange=\"onPlotModeChanged()\">\n");
        html.append("        <option value=\"stacked\" selected>Stacked (5 panels)</option>\n");
        html.append("        <option value=\"single-lhs\">Single plot (5 LHS scales)</option>\n");
        html.append("      </select>\n");
        html.append("    </div>\n");
        html.append("    <div id=\"plots-title\"></div>\n");
        html.append("    <div id=\"plots-container\"></div>\n");
        html.append("  </div>\n");
        html.append("  <script>\n");
        html.append("    var rows = ").append(jsonRows).append(";\n");
        html.append("    function showTab(name){['table','plots'].forEach(function(t){document.getElementById('tab-'+t).classList.toggle('active',t===name);});document.querySelectorAll('.tabs button').forEach(function(b,i){b.classList.toggle('active',['table','plots'][i]===name);});}\n");
        html.append("    function fmtFixed(v,dp){return(v===null||v===undefined||!isFinite(v))?'':Number(v).toFixed(dp);}\n");
        html.append("    function fmtSI(v){if(v===null||v===undefined||!isFinite(v))return '';var n=Number(v),a=Math.abs(n);if(a>=1e12)return(n/1e12).toFixed(3)+' T';if(a>=1e9)return(n/1e9).toFixed(3)+' B';if(a>=1e6)return(n/1e6).toFixed(3)+' M';if(a>=1e3)return(n/1e3).toFixed(3)+' K';return n.toFixed(4);}\n");
        html.append("    function renderTable(){var html='<table><thead><tr><th>Year</th><th>Population</th><th>Pollution Ratio</th><th>Capital Investment</th><th>Quality of Life</th><th>Natural Resources</th></tr></thead><tbody>';rows.forEach(function(r){html+='<tr><td>'+fmtFixed(r.t,1)+'</td><td>'+fmtSI(r.P)+'</td><td>'+fmtFixed(r.POLR,4)+'</td><td>'+fmtSI(r.CI)+'</td><td>'+fmtFixed(r.QL,4)+'</td><td>'+fmtSI(r.NR)+'</td></tr>';});html+='</tbody></table>';document.getElementById('table-container').innerHTML=html;document.getElementById('status').textContent='Loaded '+rows.length+' rows';}\n");
        html.append("    function onPlotModeChanged(){renderPlots();}\n");
        html.append("    function renderPlots(){var c=document.getElementById('plots-container');if(typeof Plotly==='undefined'){c.innerHTML='<div style=\\\"padding:10px;color:#c33;\\\">Plotly failed to load.</div>';return;}var t=rows.map(function(r){return r.t;});var s={t:t,P:rows.map(function(r){return r.P;}),POLR:rows.map(function(r){return r.POLR;}),CI:rows.map(function(r){return r.CI;}),QL:rows.map(function(r){return r.QL;}),NR:rows.map(function(r){return r.NR;})};var modeEl=document.getElementById('plot-mode-select');var mode=modeEl?modeEl.value:'stacked';document.getElementById('plots-title').textContent='World2 — embedded run data';if(mode==='single-lhs'){renderPlotsSingleLhs(c,s);}else{renderPlotsStacked(c,s,t);}}\n");
        html.append("    function renderPlotsStacked(c,s,t){var colors={P:'#000000',POLR:'#e7298a',CI:'#d95f02',QL:'#7570b3',NR:'#1b9e77'};var traces=[{x:t,y:s.P,mode:'lines',name:'P',line:{width:3,color:colors.P},opacity:0.7,xaxis:'x',yaxis:'y'},{x:t,y:s.POLR,mode:'lines',name:'POLR',line:{width:3,color:colors.POLR},opacity:0.7,xaxis:'x2',yaxis:'y2'},{x:t,y:s.CI,mode:'lines',name:'CI',line:{width:3,color:colors.CI},opacity:0.7,xaxis:'x3',yaxis:'y3'},{x:t,y:s.QL,mode:'lines',name:'QL',line:{width:3,color:colors.QL},opacity:0.7,xaxis:'x4',yaxis:'y4'},{x:t,y:s.NR,mode:'lines',name:'NR',line:{width:3,color:colors.NR},opacity:0.7,xaxis:'x5',yaxis:'y5'}];var layout={showlegend:false,margin:{l:70,r:20,t:20,b:35},plot_bgcolor:'#fff',paper_bgcolor:'#fff',grid:{rows:5,columns:1,pattern:'independent',roworder:'top to bottom'},xaxis:{title:'',showgrid:true,ticklen:4,tickwidth:1.5,showline:true,linecolor:'#222',linewidth:1.5},xaxis2:{title:'',showgrid:true,ticklen:4,tickwidth:1.5,showline:true,linecolor:'#222',linewidth:1.5},xaxis3:{title:'',showgrid:true,ticklen:4,tickwidth:1.5,showline:true,linecolor:'#222',linewidth:1.5},xaxis4:{title:'',showgrid:true,ticklen:4,tickwidth:1.5,showline:true,linecolor:'#222',linewidth:1.5},xaxis5:{title:'time [years]',showgrid:true,ticklen:4,tickwidth:1.5,showline:true,linecolor:'#222',linewidth:1.5},yaxis:{title:{text:'<b>P</b>',font:{color:colors.P}},range:[0,8e9],showgrid:true,tickfont:{color:colors.P},nticks:5,tickformat:'.0s',tickangle:90,showline:true,linecolor:'#222',linewidth:1.5},yaxis2:{title:{text:'<b>POLR</b>',font:{color:colors.POLR}},range:[0,40],showgrid:true,tickfont:{color:colors.POLR},nticks:5,tickformat:'.0s',tickangle:90,showline:true,linecolor:'#222',linewidth:1.5},yaxis3:{title:{text:'<b>CI</b>',font:{color:colors.CI}},range:[0,20e9],showgrid:true,tickfont:{color:colors.CI},nticks:5,tickformat:'.0s',tickangle:90,showline:true,linecolor:'#222',linewidth:1.5},yaxis4:{title:{text:'<b>QL</b>',font:{color:colors.QL}},range:[0,2],showgrid:true,tickfont:{color:colors.QL},nticks:5,tickformat:'.0s',tickangle:90,showline:true,linecolor:'#222',linewidth:1.5},yaxis5:{title:{text:'<b>NR</b>',font:{color:colors.NR}},range:[0,1000e9],showgrid:true,tickfont:{color:colors.NR},nticks:5,tickformat:'.0s',tickangle:90,showline:true,linecolor:'#222',linewidth:1.5}};Plotly.newPlot(c,traces,layout,{responsive:true,displaylogo:false,toImageButtonOptions:{format:'png',filename:'world2-plots'}});}\n");
        html.append("    function renderPlotsSingleLhs(c,s){var distSpines=0.09;var xDomainStart=0.32;var xDomainEnd=0.98;var xDomainWidth=xDomainEnd-xDomainStart;var colors={P:'#000000',POLR:'#e7298a',CI:'#d95f02',QL:'#7570b3',NR:'#1b9e77'};var names=['P','POLR','CI','QL','NR'];var limits={P:[0,8e9],POLR:[0,40],CI:[0,20e9],QL:[0,2],NR:[0,1000e9]};var traces=[{x:s.t,y:s.P,mode:'lines',name:'P',line:{width:3,color:colors.P},opacity:0.7,yaxis:'y'},{x:s.t,y:s.POLR,mode:'lines',name:'POLR',line:{width:3,color:colors.POLR},opacity:0.7,yaxis:'y2'},{x:s.t,y:s.CI,mode:'lines',name:'CI',line:{width:3,color:colors.CI},opacity:0.7,yaxis:'y3'},{x:s.t,y:s.QL,mode:'lines',name:'QL',line:{width:3,color:colors.QL},opacity:0.7,yaxis:'y4'},{x:s.t,y:s.NR,mode:'lines',name:'NR',line:{width:3,color:colors.NR},opacity:0.7,yaxis:'y5'}];var axisConfig={};for(var i=0;i<names.length;i++){var axisName=names[i];var axisKey=i===0?'yaxis':'yaxis'+(i+1);var plotAxisId=i===0?'y':'y'+(i+1);var axisPos=xDomainStart-(i*distSpines*xDomainWidth);axisConfig[axisKey]={title:{text:''},range:limits[axisName],side:'left',anchor:'free',position:axisPos,overlaying:i===0?undefined:'y',showgrid:i===0,zeroline:false,showline:true,linecolor:'#222',linewidth:1.5,tickfont:{color:colors[axisName]},ticklen:4,tickwidth:1.5,nticks:5,tickformat:'.0s',tickangle:90,automargin:true};traces[i].yaxis=plotAxisId;}var layout={showlegend:false,margin:{l:34,r:16,t:20,b:42},plot_bgcolor:'#fff',paper_bgcolor:'#fff',xaxis:{title:'time [years]',domain:[xDomainStart,xDomainEnd],showgrid:true,showline:true,linecolor:'#222',linewidth:1.5,ticklen:4,tickwidth:1.5,range:[s.t[0],s.t[s.t.length-1]]},annotations:names.map(function(name,i){var axisPos=xDomainStart-(i*distSpines*xDomainWidth);return{xref:'paper',yref:'paper',x:axisPos,y:1.01,xanchor:'center',yanchor:'bottom',text:'<b>'+name+'</b>',showarrow:false,font:{color:colors[name],size:12}};})};Object.keys(axisConfig).forEach(function(k){layout[k]=axisConfig[k];});Plotly.newPlot(c,traces,layout,{responsive:true,displaylogo:false,toImageButtonOptions:{format:'png',filename:'world2-plots-single'}});}\n");
        html.append("    renderTable();renderPlots();\n");
        html.append("  </script>\n");
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    static void appendRunParametersHtml(StringBuilder html, RunParameters p) {
        html.append("  <h3 style=\"margin:12px 0 6px 0;\">Circuit Parameters Used</h3>\n");
        html.append("  <table style=\"margin:0 0 10px 0;\">\n");
        html.append("    <thead><tr><th style=\"text-align:left\">Parameter</th><th style=\"text-align:left\">Value</th></tr></thead>\n");
        html.append("    <tbody>\n");
        appendRunParameterRow(html, "circuitPath", p.circuitPath);
        appendRunParameterRow(html, "outputPath", valueOrStdout(p.outputPath));
        appendRunParameterRow(html, "htmlPath", valueOrNone(p.htmlPath));
        appendRunParameterRow(html, "stepsRequested", Integer.toString(p.stepsRequested));
        appendRunParameterRow(html, "outputFormat", p.outputFormat + (p.world2Format ? " (world2)" : ""));
        appendRunParameterRow(html, "timestep", fmtParamNumber(p.timestep));
        appendRunParameterRow(html, "currentTimeStep", fmtParamNumber(p.currentTimeStep));
        appendRunParameterRow(html, "timeUnit", valueOrNone(p.timeUnit));
        appendRunParameterRow(html, "MnaMode", Boolean.toString(p.mnaMode));
        appendRunParameterRow(html, "equationTableTolerance", fmtParamNumber(p.equationTableTolerance));
        appendRunParameterRow(html, "lookupMode", p.lookupMode);
        appendRunParameterRow(html, "convergenceCheckThreshold", Integer.toString(p.convergenceCheckThreshold));
        appendRunParameterRow(html, "EqnTable Newton Jacobian", Boolean.toString(p.eqnTableNewtonJacobian));
        appendRunParameterRow(html, "Auto-Adjust Timestep", Boolean.toString(p.autoAdjustTimestep));
        appendRunParameterRow(html, "minTimeStep", fmtParamNumber(p.minTimeStep));
        appendRunParameterRow(html, "maxTimeStep", fmtParamNumber(p.maxTimeStep));
        appendRunParameterRow(html, "lookupClamp", Boolean.toString(p.lookupClamp));
        appendRunParameterRow(html, "computedValuesRegistered", Integer.toString(p.computedValueCount));
        html.append("    </tbody>\n");
        html.append("  </table>\n");
    }

    private static void appendRunParameterRow(StringBuilder html, String key, String value) {
        html.append("      <tr><td style=\"text-align:left\">")
                .append(escapeHtml(key))
                .append("</td><td style=\"text-align:left\">")
                .append(escapeHtml(value))
                .append("</td></tr>\n");
    }

    private static RunParameters collectRunParameters(CirSim sim, RunRequest request, boolean world2Format, int computedValueCount) {
        RunParameters parameters = new RunParameters();
        parameters.circuitPath = request.circuitPath;
        parameters.outputPath = request.outputPath;
        parameters.htmlPath = request.htmlPath;
        parameters.stepsRequested = request.steps;
        parameters.outputFormat = request.format;
        parameters.world2Format = world2Format;
        parameters.timestep = sim.maxTimeStep;
        parameters.currentTimeStep = sim.timeStep;
        parameters.timeUnit = sim.timeUnitSymbol;
        parameters.mnaMode = sim.equationTableMnaMode;
        parameters.equationTableTolerance = sim.equationTableConvergenceTolerance;
        parameters.lookupMode = sim.sfcrLookupClampDefault ? "pwl" : "pwlx";
        parameters.lookupClamp = sim.sfcrLookupClampDefault;
        parameters.convergenceCheckThreshold = sim.convergenceCheckThreshold;
        parameters.eqnTableNewtonJacobian = sim.equationTableNewtonJacobianEnabled;
        parameters.autoAdjustTimestep = sim.adjustTimeStep;
        parameters.minTimeStep = sim.minTimeStep;
        parameters.maxTimeStep = sim.maxTimeStep;
        parameters.computedValueCount = computedValueCount;
        return parameters;
    }

    private static void printRunParameters(Diagnostics diagnostics, RunParameters p) {
        log(diagnostics, "CircuitJavaRunner: circuit parameters used");
        log(diagnostics, "  circuitPath: " + p.circuitPath);
        log(diagnostics, "  outputPath: " + valueOrStdout(p.outputPath));
        log(diagnostics, "  htmlPath: " + valueOrNone(p.htmlPath));
        log(diagnostics, "  stepsRequested: " + p.stepsRequested);
        log(diagnostics, "  outputFormat: " + p.outputFormat + (p.world2Format ? " (world2)" : ""));
        log(diagnostics, "  timestep: " + fmtParamNumber(p.timestep));
        log(diagnostics, "  currentTimeStep: " + fmtParamNumber(p.currentTimeStep));
        log(diagnostics, "  timeUnit: " + valueOrNone(p.timeUnit));
        log(diagnostics, "  MnaMode: " + p.mnaMode);
        log(diagnostics, "  equationTableTolerance: " + fmtParamNumber(p.equationTableTolerance));
        log(diagnostics, "  lookupMode: " + p.lookupMode);
        log(diagnostics, "  convergenceCheckThreshold: " + p.convergenceCheckThreshold);
        log(diagnostics, "  EqnTable Newton Jacobian: " + p.eqnTableNewtonJacobian);
        log(diagnostics, "  Auto-Adjust Timestep: " + p.autoAdjustTimestep);
        log(diagnostics, "  minTimeStep: " + fmtParamNumber(p.minTimeStep));
        log(diagnostics, "  maxTimeStep: " + fmtParamNumber(p.maxTimeStep));
        log(diagnostics, "  lookupClamp: " + p.lookupClamp);
        log(diagnostics, "  computedValuesRegistered: " + p.computedValueCount);
    }

    private static void log(Diagnostics diagnostics, String message) {
        if (diagnostics != null) {
            diagnostics.log(message);
        }
    }

    private static String fmtParamNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        return PARAM_FMT.format(value);
    }

    private static String valueOrNone(String value) {
        return value == null || value.trim().isEmpty() ? "(none)" : value;
    }

    private static String valueOrStdout(String value) {
        return value == null || value.trim().isEmpty() ? "(stdout)" : value;
    }

    static void appendJsonNumber(StringBuilder out, Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            out.append("null");
            return;
        }
        out.append(PARAM_FMT.format(value.doubleValue()));
    }

    static String escapeHtml(String input) {
        String escaped = input;
        escaped = escaped.replace("&", "&amp;");
        escaped = escaped.replace("<", "&lt;");
        escaped = escaped.replace(">", "&gt;");
        escaped = escaped.replace("\"", "&quot;");
        return escaped;
    }

    private static String fileNameOnly(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static Double getNaturalResourcesValue() {
        Double nr = ComputedValues.getConvergedValue("NR");
        if (nr != null) {
            return nr;
        }
        return ComputedValues.getConvergedValue("NL");
    }

    static String fmtFixed(Double value, int decimalPlaces) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "";
        }
        if (decimalPlaces == 1) {
            return FIXED_1_FMT.format(value.doubleValue());
        }
        if (decimalPlaces == 4) {
            return FIXED_4_FMT.format(value.doubleValue());
        }
        return formatFixed(value.doubleValue(), decimalPlaces);
    }

    static String fmtFixedPrimitive(double value, int decimalPlaces) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        if (decimalPlaces == 1) {
            return FIXED_1_FMT.format(value);
        }
        if (decimalPlaces == 4) {
            return FIXED_4_FMT.format(value);
        }
        return formatFixed(value, decimalPlaces);
    }

    static String fmtSI(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "";
        }
        double n = value.doubleValue();
        double abs = Math.abs(n);
        if (abs >= 1e12) {
            return FIXED_3_FMT.format(n / 1e12) + " T";
        }
        if (abs >= 1e9) {
            return FIXED_3_FMT.format(n / 1e9) + " B";
        }
        if (abs >= 1e6) {
            return FIXED_3_FMT.format(n / 1e6) + " M";
        }
        if (abs >= 1e3) {
            return FIXED_3_FMT.format(n / 1e3) + " K";
        }
        return FIXED_4_FMT.format(n);
    }

    private static String formatFixed(double value, int decimalPlaces) {
        NumFmt.Formatter formatter = NumFmt.forPattern(buildFixedPattern(decimalPlaces));
        return formatter.format(value);
    }

    private static String buildFixedPattern(int decimalPlaces) {
        StringBuilder pattern = new StringBuilder("0");
        if (decimalPlaces > 0) {
            pattern.append('.');
            for (int i = 0; i < decimalPlaces; i++) {
                pattern.append('0');
            }
        }
        return pattern.toString();
    }
}
