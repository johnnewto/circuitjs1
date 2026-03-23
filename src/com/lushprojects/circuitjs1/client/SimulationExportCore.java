package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import com.lushprojects.circuitjs1.client.economics.ComputedValues;

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

    // Cache formatters by decimal places to avoid creating new ones repeatedly
    private static final java.util.Map<Integer, NumFmt.Formatter> FIXED_FMT_CACHE = new java.util.HashMap<Integer, NumFmt.Formatter>();

    // Parameter field definitions: {displayName, fieldKey}
    private static final String[][] RUN_PARAM_FIELDS = {
        {"circuitPath", "circuitPath"},
        {"outputPath", "outputPath"},
        {"htmlPath", "htmlPath"},
        {"stepsRequested", "stepsRequested"},
        {"outputFormat", "outputFormat"},
        {"timestep", "timestep"},
        {"currentTimeStep", "currentTimeStep"},
        {"timeUnit", "timeUnit"},
        {"MnaMode", "mnaMode"},
        {"equationTableTolerance", "equationTableTolerance"},
        {"lookupMode", "lookupMode"},
        {"convergenceCheckThreshold", "convergenceCheckThreshold"},
        {"EqnTable Newton Jacobian", "eqnTableNewtonJacobian"},
        {"Auto-Adjust Timestep", "autoAdjustTimestep"},
        {"minTimeStep", "minTimeStep"},
        {"maxTimeStep", "maxTimeStep"},
        {"lookupClamp", "lookupClamp"},
        {"computedValuesRegistered", "computedValueCount"},
    };

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
            double prevT = sim.getTime();
            sim.getSimulationLoop().runCircuit(step == 0);
            ComputedValues.commitConvergedValues();

            if (sim.stopMessage != null) {
                log(diagnostics, "CircuitJavaRunner: simulation stopped at step " + step + ": " + sim.stopMessage);
            }
            if (!warnedNoTimeAdvance && sim.getTime() == prevT) {
                warnedNoTimeAdvance = true;
                log(diagnostics, "CircuitJavaRunner: time did not advance at step " + step + " (t=" + sim.getTime() + ")");
            }

            if (world2Format) {
                Double p = ComputedValues.getConvergedValue("P");
                Double polr = ComputedValues.getConvergedValue("POLR");
                Double ci = ComputedValues.getConvergedValue("CI");
                Double ql = ComputedValues.getConvergedValue("QL");
                Double nr = getNaturalResourcesValue();
                appendWorld2Row(output, sim.getTime(), p, polr, ci, ql, nr);
                world2Rows.add(new World2Row(sim.getTime(), p, polr, ci, ql, nr));
            } else {
                appendDelimitedRow(output, sim.getTime(), keys, tsvFormat ? '\t' : ',', tsvFormat);
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
        for (String[] field : RUN_PARAM_FIELDS) {
            appendRunParameterRow(html, field[0], getRunParameterValue(p, field[1]));
        }
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
        parameters.timestep = sim.getMaxTimeStep();
        parameters.currentTimeStep = sim.getTimeStep();
        parameters.timeUnit = sim.timeUnitSymbol;
        parameters.mnaMode = sim.isEquationTableMnaMode();
        parameters.equationTableTolerance = sim.getEquationTableConvergenceTolerance();
        parameters.lookupMode = sim.isSfcrLookupClampDefault() ? "pwl" : "pwlx";
        parameters.lookupClamp = sim.isSfcrLookupClampDefault();
        parameters.convergenceCheckThreshold = sim.convergenceCheckThreshold;
        parameters.eqnTableNewtonJacobian = sim.equationTableNewtonJacobianEnabled;
        parameters.autoAdjustTimestep = sim.adjustTimeStep;
        parameters.minTimeStep = sim.getTimingState().minTimeStep;
        parameters.maxTimeStep = sim.getMaxTimeStep();
        parameters.computedValueCount = computedValueCount;
        return parameters;
    }

    private static void printRunParameters(Diagnostics diagnostics, RunParameters p) {
        log(diagnostics, "CircuitJavaRunner: circuit parameters used");
        for (String[] field : RUN_PARAM_FIELDS) {
            log(diagnostics, "  " + field[0] + ": " + getRunParameterValue(p, field[1]));
        }
    }

    private static String getRunParameterValue(RunParameters p, String fieldName) {
        switch (fieldName) {
            case "circuitPath": return p.circuitPath != null ? p.circuitPath : "";
            case "outputPath": return valueOrStdout(p.outputPath);
            case "htmlPath": return valueOrNone(p.htmlPath);
            case "stepsRequested": return Integer.toString(p.stepsRequested);
            case "outputFormat": return p.outputFormat + (p.world2Format ? " (world2)" : "");
            case "timestep": return fmtParamNumber(p.timestep);
            case "currentTimeStep": return fmtParamNumber(p.currentTimeStep);
            case "timeUnit": return valueOrNone(p.timeUnit);
            case "mnaMode": return Boolean.toString(p.mnaMode);
            case "equationTableTolerance": return fmtParamNumber(p.equationTableTolerance);
            case "lookupMode": return p.lookupMode != null ? p.lookupMode : "";
            case "convergenceCheckThreshold": return Integer.toString(p.convergenceCheckThreshold);
            case "eqnTableNewtonJacobian": return Boolean.toString(p.eqnTableNewtonJacobian);
            case "autoAdjustTimestep": return Boolean.toString(p.autoAdjustTimestep);
            case "minTimeStep": return fmtParamNumber(p.minTimeStep);
            case "maxTimeStep": return fmtParamNumber(p.maxTimeStep);
            case "lookupClamp": return Boolean.toString(p.lookupClamp);
            case "computedValueCount": return Integer.toString(p.computedValueCount);
            default: return "";
        }
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
        if (value == null) {
            return "";
        }
        return fmtFixedPrimitive(value.doubleValue(), decimalPlaces);
    }

    static String fmtFixedPrimitive(double value, int decimalPlaces) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        switch (decimalPlaces) {
            case 1: return FIXED_1_FMT.format(value);
            case 3: return FIXED_3_FMT.format(value);
            case 4: return FIXED_4_FMT.format(value);
            default: return getCachedFormatter(decimalPlaces).format(value);
        }
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

    private static NumFmt.Formatter getCachedFormatter(int decimalPlaces) {
        NumFmt.Formatter formatter = FIXED_FMT_CACHE.get(decimalPlaces);
        if (formatter == null) {
            formatter = NumFmt.forPattern(buildFixedPattern(decimalPlaces));
            FIXED_FMT_CACHE.put(decimalPlaces, formatter);
        }
        return formatter;
    }

    private static String buildFixedPattern(int decimalPlaces) {
        if (decimalPlaces <= 0) {
            return "0";
        }
        StringBuilder pattern = new StringBuilder("0.");
        for (int i = 0; i < decimalPlaces; i++) {
            pattern.append('0');
        }
        return pattern.toString();
    }

    // ========== Runner HTML builders ==========

    static String buildDelimitedHtmlReport(String outputText, char separator, String source, int steps) {
        return buildDelimitedHtmlReport(outputText, separator, source, steps, null);
    }

    static String buildDelimitedHtmlReport(String outputText, char separator, String source, int steps, Set<String> stockNames) {
        StringBuilder content = new StringBuilder();
        content.append("<div style='margin-top:10px; font-weight:600;'>Output Table</div>");
        content.append(buildDelimitedOutputTableHtml(outputText, separator, stockNames));
        content.append("<details style='margin-top:10px;'><summary>Raw Output</summary>");
        content.append("<pre style='white-space:pre; font-family:monospace; max-height:70vh; overflow:auto; border:1px solid #ccc; padding:8px; margin-top:6px;'>");
        content.append(escapeHtml(outputText != null ? outputText : ""));
        content.append("</pre></details>");

        String plotHtml = buildDelimitedPlotReportHtml(outputText, separator, source, steps, stockNames);
        String escapedPlotHtml = escapeHtmlAttribute(plotHtml);

        return "<div style='padding:12px;'>"
            + "<div style='margin:8px 0;'>"
            + "<button onclick=\"document.getElementById('runner-table-tab').style.display='block';document.getElementById('runner-plot-tab').style.display='none';\">Output Table</button>"
            + "<button style='margin-left:8px;' onclick=\"document.getElementById('runner-table-tab').style.display='none';document.getElementById('runner-plot-tab').style.display='block';\">Plot</button>"
            + "</div>"
            + "<div id='runner-table-tab' style='display:block;'>"
            + content.toString()
            + "</div>"
            + "<div id='runner-plot-tab' style='display:none; margin-top:8px;'>"
            + "<iframe style='width:100%; height:78vh; border:1px solid #ccc;' srcdoc=\""
            + escapedPlotHtml
            + "\"></iframe></div>"
            + "</div>";
    }

    private static String buildDelimitedOutputTableHtml(String outputText, char separator, Set<String> stockNames) {
        if (outputText == null || outputText.isEmpty()) {
            return "<div style='margin-top:6px; color:#777;'>(no output)</div>";
        }
        String[] lines = outputText.split("\\r?\\n");
        if (lines.length == 0 || lines[0].isEmpty()) {
            return "<div style='margin-top:6px; color:#777;'>(no output)</div>";
        }
        String splitRegex = (separator == '\t') ? "\\t" : ",";
        String[] headers = lines[0].split(splitRegex, -1);
        StringBuilder html = new StringBuilder();
        String tableId = "runner-output-table";
        String checkboxId = "runner-filter-stocks";
        String tableWrapId = "runner-stock-table-wrap";
        String tableWrapClass = "runner-stock-table-wrap";
        boolean hasStockFilterableColumns = false;
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i] != null ? headers[i].trim() : "";
            if ("t".equals(header)) {
                continue;
            }
            if (stockNames != null && stockNames.contains(header)) {
                hasStockFilterableColumns = true;
                break;
            }
        }

        if (hasStockFilterableColumns) {
            html.append("<style>")
                .append(".").append(tableWrapClass).append(".stocks-only th[data-stock='0'],")
                .append(".").append(tableWrapClass).append(".stocks-only td[data-stock='0']")
                .append("{display:none;}")
                .append("</style>");
            html.append("<div style='margin:6px 0 8px 0;'><label style='font-size:12px; user-select:none;'><input type='checkbox' id='")
                .append(checkboxId)
                .append("' onchange=\"var w=document.getElementById('")
                .append(tableWrapId)
                .append("');if(w){if(this.checked){w.classList.add('stocks-only');}else{w.classList.remove('stocks-only');}}\" /> Stocks only</label></div>");
        }

        html.append("<div id='").append(tableWrapId).append("' class='").append(tableWrapClass).append("' style='margin-top:6px; max-width:100%; max-height:70vh; overflow:auto; border:1px solid #ccc;'>");
        html.append("<table id='").append(tableId).append("' style='border-collapse:collapse; min-width:max-content; font-family:monospace; font-size:12px;'>");

        html.append("<thead><tr>");
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i] != null ? headers[i].trim() : "";
            boolean isStockColumn = stockNames != null && stockNames.contains(header);
            String stockAttr = ("t".equals(header) || isStockColumn) ? "1" : "0";
            html.append("<th data-stock='").append(stockAttr).append("' style='text-align:left; white-space:nowrap; border:1px solid #ddd; padding:4px 8px; background:#f6f6f6; position:sticky; top:0; z-index:1;'>");
            html.append(escapeHtml(headers[i]));
            html.append("</th>");
        }
        html.append("</tr></thead><tbody>");

        for (int lineIdx = 1; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];
            if (line == null || line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(splitRegex, -1);
            html.append("<tr>");
            for (int colIdx = 0; colIdx < headers.length; colIdx++) {
                String value = colIdx < cols.length ? cols[colIdx] : "";
                String header = headers[colIdx] != null ? headers[colIdx].trim() : "";
                boolean isStockColumn = stockNames != null && stockNames.contains(header);
                String stockAttr = ("t".equals(header) || isStockColumn) ? "1" : "0";
                html.append("<td style='white-space:nowrap; border:1px solid #eee; padding:3px 8px;' data-stock='")
                    .append(stockAttr)
                    .append("'>");
                html.append(escapeHtml(value));
                html.append("</td>");
            }
            html.append("</tr>");
        }

        html.append("</tbody></table></div>");
        return html.toString();
    }

    private static String buildDelimitedPlotReportHtml(String outputText, char separator, String source, int steps, Set<String> stockNames) {
        String sepLiteral = separator == '\t' ? "\\t" : ",";
        String escapedData = escapeHtml(outputText != null ? outputText : "");
        String escapedSource = escapeHtml(source != null ? source : "(none)");
        String stockNamesJs = toJsStringArray(stockNames);
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        html.append("<title>Runner Plot</title>");
        html.append("<script src='https://cdn.plot.ly/plotly-2.27.0.min.js'></script>");
        appendDelimitedPlotStyles(html);
        html.append("<div style='font-weight:600;margin-bottom:4px;'>Runner Plot</div>");
        html.append("<div class='meta'>Source: ").append(escapedSource).append(" &mdash; Steps: ").append(steps).append("</div>");
        html.append("<textarea id='runner-data' style='display:none;'>").append(escapedData).append("</textarea>");
        appendDelimitedPlotControls(html);
        html.append("<div id='plot-container'></div>");
        appendDelimitedPlotScript(html, sepLiteral, stockNamesJs);
        return html.toString();
    }

    private static void appendDelimitedPlotStyles(StringBuilder html) {
        html.append("<style>");
        html.append("body{font-family:Arial,sans-serif;margin:0;padding:12px;background:#fff;color:#111;}");
        html.append(".meta{color:#666;margin-bottom:10px;font-size:12px;}");
        html.append(".controls{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:10px;}");
        html.append("label{font-size:12px;color:#333;}");
        html.append("select{font-size:12px;padding:2px 4px;}");
        html.append("#plot-container{min-height:320px;border:1px solid #ddd;padding:8px;}");
        html.append(".stack-plot{height:180px;margin-bottom:10px;}");
        html.append("</style></head><body>");
    }

    private static void appendDelimitedPlotControls(StringBuilder html) {
        html.append("<div class='controls'>");
        html.append("<label for='plot-mode-select'>Plot mode:</label>");
        html.append("<select id='plot-mode-select'><option value='stacked'>Stacked (panels)</option><option value='single-lhs' selected>Single plot (5 LHS scales)</option></select>");
        html.append("<label style='user-select:none;'><input id='plot-stocks-only' type='checkbox'/> Stocks only</label>");
        html.append("<label style='user-select:none;'><input id='plot-align-zero' type='checkbox'/> Align LHS at zero</label>");
        html.append("<label for='y1'>Y1:</label><select id='y1'></select>");
        html.append("<label for='y2'>Y2:</label><select id='y2'></select>");
        html.append("<label for='y3'>Y3:</label><select id='y3'></select>");
        html.append("<label for='y4'>Y4:</label><select id='y4'></select>");
        html.append("<label for='y5'>Y5:</label><select id='y5'></select>");
        html.append("</div>");
    }

    private static void appendDelimitedPlotScript(StringBuilder html, String sepLiteral, String stockNamesJs) {
        html.append("<script>");
        html.append("(function(){");
        html.append("var raw=document.getElementById('runner-data').value||'';");
        html.append("var sep='").append(sepLiteral).append("';");
        html.append("var stockNames=").append(stockNamesJs).append(";");
        html.append("var lines=raw.split(/\\r?\\n/).filter(function(l){return l.length>0;});");
        html.append("var container=document.getElementById('plot-container');");
        html.append("if(typeof Plotly==='undefined'){container.innerHTML='<div style=\"color:#c33;padding:8px;\">Plotly failed to load.</div>';return;}");
        html.append("if(lines.length<2){container.innerHTML='<div style=\"color:#777;padding:8px;\">Not enough data to plot.</div>';return;}");
        html.append("var headers=lines[0].split(sep);");
        html.append("var rows=lines.slice(1).map(function(line){return line.split(sep);});");
        html.append("var tIndex=headers.indexOf('t'); if(tIndex<0) tIndex=0;");
        html.append("var candidatesAll=[]; for(var i=0;i<headers.length;i++){ if(i!==tIndex) candidatesAll.push(headers[i]); }");
        html.append("if(candidatesAll.length===0){container.innerHTML='<div style=\"color:#777;padding:8px;\">No Y variables available.</div>';return;}");
        html.append("var stockSet={}; for(var i=0;i<stockNames.length;i++){stockSet[stockNames[i]]=true;}");
        html.append("var stockCandidates=candidatesAll.filter(function(n){return !!stockSet[n];});");
        html.append("var series={}; headers.forEach(function(h){series[h]=rows.map(function(r){var v=parseFloat((r[headers.indexOf(h)]||'').trim()); return isFinite(v)?v:null;});});");
        html.append("var tValues=series[headers[tIndex]];");
        html.append("var selects=['y1','y2','y3','y4','y5'].map(function(id){return document.getElementById(id);});");
        html.append("var stocksOnlyEl=document.getElementById('plot-stocks-only');");
        html.append("var alignZeroEl=document.getElementById('plot-align-zero');");
        html.append("function activeCandidates(){if(stocksOnlyEl&&stocksOnlyEl.checked&&stockCandidates.length>0)return stockCandidates;return candidatesAll;}");
        html.append("function fillSelect(sel, def, opts){var list=['']; for(var i=0;i<opts.length;i++) list.push(opts[i]); if(def&&list.indexOf(def)<0) def=''; sel.innerHTML=list.map(function(v){var lbl=v||'(none)'; var s=(v===def)?' selected':''; return '<option value=\\\"'+v+'\\\"'+s+'>'+lbl+'</option>';}).join('');}");
        html.append("function refreshSelects(){var opts=activeCandidates(); for(var i=0;i<selects.length;i++){var current=selects[i].value||''; var def=current||(opts[i]||''); fillSelect(selects[i], def, opts);} }");
        html.append("refreshSelects();");
        html.append("var palette=['#1f77b4','#d62728','#2ca02c','#9467bd','#ff7f0e','#17becf','#8c564b'];");
        html.append("function computeRange(values){var min=Infinity,max=-Infinity; for(var i=0;i<values.length;i++){var v=values[i]; if(v===null||!isFinite(v)) continue; if(v<min) min=v; if(v>max) max=v;} if(min===Infinity||max===-Infinity) return [-1,1]; if(min===max){var pad=Math.max(Math.abs(min)*0.05,1); return [min-pad,max+pad];} var span=max-min; var pad=Math.max(span*0.05,1e-9); return [min-pad,max+pad];}");
        html.append("function computeTickValues(range,tickCount){var out=[]; var start=range[0], end=range[1]; if(tickCount<=1){out.push(start); return out;} var step=(end-start)/(tickCount-1); for(var i=0;i<tickCount;i++){out.push(start+step*i);} return out;}");
        html.append("function selectedNames(){var seen={}; var out=[]; for(var i=0;i<selects.length;i++){var n=selects[i].value; if(n && !seen[n]){seen[n]=true; out.push(n);} } return out;}");
        html.append("function buildXAxis(title){return {title:title||'',showgrid:true,automargin:true};}");
        html.append("function renderStacked(names){container.innerHTML=''; if(names.length===0){container.innerHTML='<div style=\"color:#777;padding:8px;\">Select at least one Y variable.</div>'; return;} for(var i=0;i<names.length;i++){var name=names[i]; var div=document.createElement('div'); div.className='stack-plot'; container.appendChild(div); Plotly.newPlot(div,[{x:tValues,y:series[name],mode:'lines',name:name,line:{width:2,color:palette[i%palette.length]}}],{margin:{l:50,r:20,t:20,b:35},xaxis:buildXAxis(i===names.length-1?'t':''),yaxis:{title:name},showlegend:false},{responsive:true,displaylogo:false});}}");
        html.append("function renderSingleLhs(names){container.innerHTML=''; if(names.length===0){container.innerHTML='<div style=\"color:#777;padding:8px;\">Select at least one Y variable.</div>'; return;} var div=document.createElement('div'); div.style.height='520px'; container.appendChild(div); var traces=[]; var distSpines=0.09; var xDomainStart=Math.min(0.62,0.32+(5-names.length)*0.05); var xDomainEnd=0.98; var xDomainWidth=xDomainEnd-xDomainStart; var tickCount=5; var alignZero=!!(alignZeroEl&&alignZeroEl.checked); var dataExtents=[]; var ranges=[]; for(var j=0;j<names.length;j++){var yData0=series[names[j]]; var min=Infinity,max=-Infinity; for(var k=0;k<yData0.length;k++){var vv=yData0[k]; if(vv===null||!isFinite(vv)) continue; if(vv<min) min=vv; if(vv>max) max=vv;} if(min===Infinity||max===-Infinity){min=0;max=0;} dataExtents.push([min,max]); ranges.push(computeRange(yData0));} if(alignZero){var globalNeg=0,globalPos=0; for(var j=0;j<dataExtents.length;j++){globalNeg=Math.max(globalNeg,Math.max(0,-dataExtents[j][0])); globalPos=Math.max(globalPos,Math.max(0,dataExtents[j][1]));} var zeroFrac=(globalNeg+globalPos>0)?(globalNeg/(globalNeg+globalPos)):0.5; for(var j=0;j<dataExtents.length;j++){var dmin=dataExtents[j][0], dmax=dataExtents[j][1]; if(zeroFrac<=0){ranges[j]=[0,Math.max(dmax,1e-9)];} else if(zeroFrac>=1){ranges[j]=[Math.min(dmin,-1e-9),0];} else {var span=Math.max(dmax/(1-zeroFrac),(-dmin)/zeroFrac); if(!isFinite(span)||span<=0) span=1; ranges[j]=[-zeroFrac*span,(1-zeroFrac)*span];}}} var layout={showlegend:false,margin:{l:34,r:16,t:20,b:42},plot_bgcolor:'#fff',paper_bgcolor:'#fff',xaxis:{title:'time [years]',domain:[xDomainStart,xDomainEnd],showgrid:true,showline:true,linecolor:'#222',linewidth:1.5,ticklen:4,tickwidth:1.5,range:[tValues[0],tValues[tValues.length-1]]}}; for(var i=0;i<names.length;i++){var axisRef=i===0?'y':'y'+(i+1); var color=palette[i%palette.length]; var yData=series[names[i]]; traces.push({x:tValues,y:yData,mode:'lines',name:names[i],line:{width:3,color:color},opacity:0.7,yaxis:axisRef}); var axisName='yaxis'+(i===0?'':(i+1)); var axisPos=xDomainStart-(i*distSpines*xDomainWidth); var range=ranges[i]; var axisTicks=computeTickValues(range,tickCount); var axis={title:{text:''},side:'left',anchor:'free',position:axisPos,showgrid:i===0,zeroline:alignZero&&range[0]<=0&&range[1]>=0,showline:true,linecolor:'#222',linewidth:1.5,tickfont:{color:color},ticklen:4,tickwidth:1.5,tickmode:'array',tickvals:axisTicks,tickformat:'.0s',tickangle:90,automargin:true,range:range}; if(i!==0){axis.overlaying='y';} layout[axisName]=axis;} layout.annotations=names.map(function(name,i){var axisPos=xDomainStart-(i*distSpines*xDomainWidth); return{xref:'paper',yref:'paper',x:axisPos,y:1.01,xanchor:'center',yanchor:'bottom',text:'<b>'+name+'</b>',showarrow:false,font:{color:palette[i%palette.length],size:12}};}); Plotly.newPlot(div,traces,layout,{responsive:true,displaylogo:false});}");
        html.append("function render(){var names=selectedNames(); var mode=document.getElementById('plot-mode-select').value; if(mode==='single-lhs') renderSingleLhs(names); else renderStacked(names);}");
        html.append("document.getElementById('plot-mode-select').addEventListener('change',render);");
        html.append("selects.forEach(function(s){s.addEventListener('change',render);});");
        html.append("if(stocksOnlyEl){stocksOnlyEl.addEventListener('change',function(){refreshSelects();render();});}");
        html.append("if(alignZeroEl){alignZeroEl.addEventListener('change',render);}");
        html.append("render();");
        html.append("})();");
        html.append("</script></body></html>");
    }

    private static String toJsStringArray(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> list = new ArrayList<String>(values);
        Collections.sort(list);
        StringBuilder js = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                js.append(',');
            }
            js.append('"').append(escapeJsString(list.get(i))).append('"');
        }
        js.append(']');
        return js.toString();
    }

    private static String escapeJsString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String buildRunnerStatusContentHtml(String message) {
        return "<div id='runner-status-message'>" + escapeHtml(message != null ? message : "") + "</div>"
            + "<div style='color:#666; margin-top:6px;'>Format hint: default is <b>tsv</b> when omitted (use format=csv or format=world2 to override).</div>";
    }

    static String buildRunnerErrorContentHtml(String message) {
        return "<div style='color:#c33;'>" + escapeHtml(message != null ? message : "") + "</div>";
    }

    static String buildRunnerSummaryContentHtml(String source, int requestedSteps, String format, int completedSteps) {
        StringBuilder content = new StringBuilder();
        content.append("<div><b>Source:</b> ").append(escapeHtml(source != null ? source : "(none)")).append("</div>");
        content.append("<div><b>Requested steps:</b> ").append(requestedSteps).append("</div>");
        content.append("<div><b>Output format:</b> ").append(escapeHtml(format != null ? format : "")).append("</div>");
        content.append("<div style='color:#666; margin-top:4px;'>Format hint: default is <b>tsv</b> when omitted (use format=csv or format=world2 to override).</div>");
        content.append("<div><b>Completed steps:</b> ").append(completedSteps).append("</div>");
        return content.toString();
    }

    static String buildRunnerWorld2RawOutputHtml(String outputText) {
        return "<div style='margin-top:10px;'><pre style='white-space:pre; font-family:monospace; max-height:70vh; overflow:auto; border:1px solid #ccc; padding:8px;'>"
            + escapeHtml(outputText != null ? outputText : "")
            + "</pre></div>";
    }

    static String buildRunnerWorld2ReportTabHtml(String htmlReport) {
        if (htmlReport == null) {
            return "";
        }
        return "<div id='runner-report-tab' style='display:none; margin-top:8px;'>"
            + "<iframe style='width:100%; height:78vh; border:1px solid #ccc;' srcdoc=\""
            + escapeHtmlAttribute(htmlReport)
            + "\"></iframe></div>";
    }

    static String buildRunnerTabbedHtml(String primaryTabTitle, String primaryContentHtml,
            boolean includeReportTab, String reportTabTitle, String reportTabContentHtml,
            String stdoutHtml) {
        String escapedTitle = escapeHtml(primaryTabTitle);
        String escapedReportTitle = escapeHtml(reportTabTitle != null ? reportTabTitle : "Report");
        String safeStdoutHtml = stdoutHtml != null ? stdoutHtml : escapeHtml("(no output yet)");
        String reportButton = includeReportTab
            ? "<button style='margin-left:8px;' onclick=\"document.getElementById('runner-primary-tab').style.display='none';document.getElementById('runner-report-tab').style.display='block';document.getElementById('runner-stdout-tab').style.display='none';\">" + escapedReportTitle + "</button>"
            : "";
        return "<div style='padding:12px;'>"
            + "<h2>Browser Runner Output</h2>"
            + "<div style='margin:8px 0;'>"
            + "<button onclick=\"document.getElementById('runner-primary-tab').style.display='block';"
                + (includeReportTab ? "document.getElementById('runner-report-tab').style.display='none';" : "")
                + "document.getElementById('runner-stdout-tab').style.display='none';\">"
            + escapedTitle
            + "</button>"
            + reportButton
            + "<button style='margin-left:8px;' onclick=\"document.getElementById('runner-primary-tab').style.display='none';"
                + (includeReportTab ? "document.getElementById('runner-report-tab').style.display='none';" : "")
                + "document.getElementById('runner-stdout-tab').style.display='block';\">Standard Output</button>"
            + "</div>"
            + "<div id='runner-primary-tab' style='display:block;'>"
            + primaryContentHtml
            + "</div>"
            + reportTabContentHtml
            + "<div id='runner-stdout-tab' style='display:none;'>"
            + "<div id='runner-stdout-pre' style='white-space:pre-wrap; font-family:monospace; max-height:70vh; overflow:auto; border:1px solid #ccc; padding:8px;'>"
            + safeStdoutHtml
            + "</div>"
            + "</div>"
            + "</div>";
    }

    private static String escapeHtmlAttribute(String input) {
        if (input == null) {
            return "";
        }
        return escapeHtml(input);
    }

    // ========== Runner table / non-interactive HTML builders ==========

    static String buildRunnerTableDiv(String innerHtml) {
        return "<div>" + (innerHtml != null ? innerHtml : "") + "</div>";
    }

    static String buildRunnerTableStyledDiv(String style, String innerHtml) {
        return "<div style='" + (style != null ? style : "") + "'>" + (innerHtml != null ? innerHtml : "") + "</div>";
    }

    static String buildRunnerTableCell(String value) {
        return "<td>" + escapeHtml(value != null ? value : "") + "</td>";
    }

    static String buildRunnerTableRow(List<String> cells) {
        return "<tr>" + String.join("", cells) + "</tr>";
    }

    static String buildRunnerTableHeader(List<String> keys) {
        List<String> headers = new ArrayList<String>();
        headers.add("<th>t</th>");
        for (int i = 0; i < keys.size(); i++) {
            headers.add("<th>" + escapeHtml(keys.get(i)) + "</th>");
        }
        return "<thead><tr>" + String.join("", headers) + "</tr></thead>";
    }

    static String buildRunnerTableWrapperOpen() {
        return "<div style='margin-top:10px; max-height:70vh; overflow:auto;'>";
    }

    static String buildRunnerTableOpen() {
        return "<table border='1' cellspacing='0' cellpadding='4'>";
    }

    static String buildRunnerTableBodyOpen() {
        return "<tbody>";
    }

    static String buildRunnerTableWrapperClose() {
        return "</tbody></table></div>";
    }

    static String buildRunnerTableStatusContentHtml(String message) {
        return "<div id='runner-status-message'>" + escapeHtml(message != null ? message : "") + "</div>";
    }

    static String buildRunnerTableErrorContentHtml(String message) {
        return "<div style='color:#c33;'>" + escapeHtml(message != null ? message : "") + "</div>";
    }

    static String buildRunnerTableTabbedHtml(String primaryTabTitle, String primaryContentHtml, String stdoutHtml) {
        String escapedTitle = escapeHtml(primaryTabTitle != null ? primaryTabTitle : "");
        String safeStdoutHtml = stdoutHtml != null ? stdoutHtml : escapeHtml("(no output yet)");
        return "<div style='padding:12px;'>"
            + "<h2>Runner Output Table</h2>"
            + "<div style='margin:8px 0;'>"
            + "<button onclick=\"document.getElementById('runner-table-primary-tab').style.display='block';document.getElementById('runner-table-stdout-tab').style.display='none';\">"
            + escapedTitle
            + "</button>"
            + "<button style='margin-left:8px;' onclick=\"document.getElementById('runner-table-primary-tab').style.display='none';document.getElementById('runner-table-stdout-tab').style.display='block';\">Standard Output</button>"
            + "</div>"
            + "<div id='runner-table-primary-tab' style='display:block;'>"
            + (primaryContentHtml != null ? primaryContentHtml : "")
            + "</div>"
            + "<div id='runner-table-stdout-tab' style='display:none;'>"
            + "<div id='runner-stdout-pre' style='white-space:pre-wrap; font-family:monospace; max-height:70vh; overflow:auto; border:1px solid #ccc; padding:8px;'>"
            + safeStdoutHtml
            + "</div>"
            + "</div>"
            + "</div>";
    }
}
