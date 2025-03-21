import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { IPopoverConfig } from '@liO/ui';
import { HighchartserviceService } from 'app/services/highchartservice.service';
import { ManageserviceService } from 'app/services/manageservice.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-porFolio-management',
  templateUrl: './porFolio-management.component.html',
  styleUrls: ['./porFolio-management.component.scss']
})
export class PorFolioManagementComponent implements OnInit, OnDestroy {
  PorFolioManagementChart = [];
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
        this.getChart();
      }
    });
  }

  LoadPopoverdetails() {
    this.curr_qtr_value = 0;
    this.prv_qtr_value = 0;
    this.qtr_var = 0;
    this.curr_fyytd_value = 0;
    this.prev_fyytd_value = 0;
    this.three_yr_avg = 0;

    if (!this.Popoverdetails || this.Popoverdetails.length === 0) {
      console.error('Popoverdetails is empty or undefined');
      return;
    }

    const ReportDate = this.Popoverdetails[0]?.report_date;
    if (ReportDate) {
      const Years = ReportDate.split('-');
      const FY = Years[0].substring(2);

      this.Popoverdetails.forEach((y, index) => {
        if (y.curr_fyqtr_value) {
          this.curr_qtr_value += parseFloat(y.curr_fyqtr_value);
        }
        if (y.prev_fyqtr_value) {
          this.prv_qtr_value += parseFloat(y.prev_fyqtr_value);
        }
        if (y.curr_fyytd_value) {
          this.curr_fyytd_value += parseFloat(y.curr_fyytd_value);
        }
        if (y.prev_fyytd_value) {
          this.prev_fyytd_value += parseFloat(y.prev_fyytd_value);
        }
        if (y.prev_three_fy_avg_value) {
          this.three_yr_avg += parseFloat(y.prev_three_fy_avg_value);
        }
      });

      this.qtr_var = this.prv_qtr_value === 0 ? 0 : ((this.curr_qtr_value - this.prv_qtr_value) / this.prv_qtr_value * 100).toFixed(1);
      this.fyytd_var = this.prev_fyytd_value === 0 ? 0 : ((this.curr_fyytd_value - this.prev_fyytd_value) / this.prev_fyytd_value * 100).toFixed(1);
      this.varience = this.qtr_var;
    } else {
      console.error('ReportDate is null or undefined');
    }
  }

  @HostListener('window:resize', ['$event'])
  onResize(event: Event) {
    this.getChart();
  }

  ngOnDestroy(): void {
    this.destroy.next('');
    this.destroy.complete();
  }

  GetDetailPage() {}
}
