import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { IPopoverConfig } from '@liO/ui';
import { HighchartserviceService } from 'app/services/highchartservice.service';
import { ManageserviceService } from 'app/services/manageservice.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-portfolio-management',
  templateUrl: './portfolio-management.component.html',
  styleUrls: ['./portfolio-management.component.scss']
})
export class PortfolioManagementComponent implements OnInit, OnDestroy {
  PortfolioManagementChart = [];
  LegendHtml = [];
  infoDetail: any;
  subscribeData: any;
  ResponseFlag: any = false;
  config: IPopoverConfig = {
    showPopoverOnClick: true,
  };
  private destroy = new Subject();
  chartnumber: any = 'charts';
  Popoverdetails: any;
  curr_qtr_value: number = 0;
  prv_qtr_value: number = 0;
  qtr_var: any = 0;
  curr_fyytd_value: any = 0;
  prev_fyytd_value: number = 0;
  fyytd_var: any = 0;
  curr_fy_value: number = 0;
  prev_fy_value: number = 0;
  fy_var: any = 0;
  crtQ2: string;
  crtQ1: string;
  pre_fy: string;
  pre_prev_fy: string;
  during_crt_fy: any;
  during_prev_fy: any;
  fy_date_pre: string;
  fy_date_pre_prev: string;
  full_yr_crt: string;
  full_yr_pre: string;
  avg_year: string;
  varience: any = 0;
  current_value: any = 0;
  previous_value: any = 0;
  current_lable: any;
  previous_label: any;
  three_yr_avg: any = 0;
  xAxisCategory = [];
  selectedVal = 'qtr';
  @ViewChild('cartboxchartsec5on') cartboxchartsec5on: ElementRef;
  dataseries = [];

  constructor(private highChartsService: HighchartserviceService, private _Manageservice: ManageserviceService) {
    if (this.subscribeData) {
      this.subscribeData.unsubscribe();
    }
    this.subscribeData = this._Manageservice.filterApply.pipe(takeUntil(this.destroy)).subscribe((response: any) => {
      if (response.command === 'filterApply') {
        this.onInitLoad();
      }
    });
    this._Manageservice.ChartApply.subscribe((response: any) => {
      if (response.command === 'chartvalue') {
        this.chartnumber = this._Manageservice.charFilters;
      }
    });
  }

  ngOnInit(): void { }

  onInitLoad() {
    const params = {
      'widget_id': 'THM_12%',
      'report_date': this._Manageservice.reportdate,
      'org_code': 'WB',
      'module': 'thema5c',
      'displaytype': 'widget'
    };
    this._Manageservice.PostApiCall('/api/managementDB/getMgmtSummaryData', params).subscribe((data: any) => {
      this.ResponseFlag = true;
      if (data && data.length > 0) {
        this.Popoverdetails = data;
        this.LoadPopoverdetails();
        this.getPortfolioManagementChart();
      }
    });
  }

  getPortfolioManagementChart() {
    this.LegendHtml = [];
    this.dataseries = [];
    this.xAxisCategory = ['People', 'Planet', 'Prosp.', 'Infrast.', 'Digital Trans'];

    const curryr = this.Popoverdetails.map((y: any) => y.curr_fyytd_value || 0);
    const prevyr = this.Popoverdetails.map((y: any) => y.prev_fyytd_value || 0);

    this.dataseries = [
      {
        name: 'FY24',
        data: curryr,
        color: '#66C4CA',
        pointWidth: 15,
        borderRadius: 3,
        dataLabels: {
          enabled: true,
          formatter: function () { return this.y; }
        },
        tooltip: { pointFormat: '{series.name}: <b>{point.y:,.1f}B</b>' }
      },
      {
        name: 'FY23',
        data: prevyr,
        color: '#009CA7',
        pointWidth: 15,
        borderRadius: 3,
        dataLabels: {
          enabled: true,
          formatter: function () { return this.y; }
        },
        tooltip: { pointFormat: '{series.name}: <b>{point.y:,.1f}B</b>' }
      }
    ];

    const chartWidth = this.cartboxchartsec5on.nativeElement.offsetWidth;
    const ChartOptions = {
      chartTitle: 'Portfolio Management',
      chartWidth: chartWidth,
      chartHeight: 210,
      xAxisCategory: this.xAxisCategory,
      dataseries: this.dataseries,
      legendVisible: false,
      xAxisTitle: '',
      xAxisVisible: true,
      yAxisVisible: true,
      dataLabelEnable: true,
      yAxisTitle: 'Amount ($B)'
    };

    this.LegendHtml = this.dataseries;
    this.PortfolioManagementChart = this.highChartsService.GetColumnChart(ChartOptions);
  }

  @HostListener('window:resize', ['$event'])
  onResize(event: Event) {
    this.getPortfolioManagementChart();
  }

  ngOnDestroy(): void {
    this.destroy.next('');
    this.destroy.complete();
  }

  GetDetailPage() {}
}
