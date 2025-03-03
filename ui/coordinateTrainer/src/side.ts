import { h, VNode, VNodes } from 'snabbdom';
import { bind } from 'common/snabbdom';
import CoordinateTrainerCtrl from './ctrl';
import { ColorChoice, TimeControl, Mode } from './interfaces';
import { toggle } from 'common/toggle';

const colors: [ColorChoice, string][] = [
  ['black', 'asBlack'],
  ['random', 'randomColor'],
  ['white', 'asWhite'],
];

const timeControls: [TimeControl, string][] = [
  ['untimed', '∞'],
  ['thirtySeconds', '0:30'],
];

const configurationButtons = (ctrl: CoordinateTrainerCtrl): VNodes => [
  h('form.mode.buttons', [
    h(
      'group.radio',
      ['findSquare', 'nameSquare'].map((mode: Mode) =>
        h('div.mode_option', [
          h('input', {
            attrs: {
              type: 'radio',
              id: `coord_mode_${mode}`,
              name: 'mode',
              value: mode,
              checked: mode === ctrl.mode(),
            },
            on: {
              change: e => {
                const target = e.target as HTMLInputElement;
                ctrl.mode(target.value as Mode);
              },
              keyup: ctrl.onRadioInputKeyUp,
            },
          }),
          h(
            `label.mode_${mode}`,
            {
              attrs: {
                for: `coord_mode_${mode}`,
                title: ctrl.trans(mode === 'findSquare' ? 'aSquareNameAppears' : 'aSquareIsHighlighted'),
              },
            },
            ctrl.trans(mode)
          ),
        ])
      )
    ),
  ]),
  h('form.timeControl.buttons', [
    h(
      'group.radio',
      timeControls.map(([timeControl, timeControlLabel]) =>
        h('div.timeControl_option', [
          h('input', {
            attrs: {
              type: 'radio',
              id: `coord_timeControl_${timeControl}`,
              name: 'timeControl',
              value: timeControl,
              checked: timeControl === ctrl.timeControl(),
            },
            on: {
              change: e => {
                const target = e.target as HTMLInputElement;
                ctrl.timeControl(target.value as TimeControl);
              },
              keyup: ctrl.onRadioInputKeyUp,
            },
          }),
          h(
            `label.timeControl_${timeControl}`,
            {
              attrs: {
                for: `coord_timeControl_${timeControl}`,
                title: ctrl.trans(timeControl === 'thirtySeconds' ? 'youHaveThirtySeconds' : 'goAsLongAsYouWant'),
              },
            },
            timeControlLabel
          ),
        ])
      )
    ),
  ]),
  h('form.color.buttons', [
    h(
      'group.radio',
      colors.map(([key, i18n]) =>
        h('div', [
          h('input', {
            attrs: {
              type: 'radio',
              id: `coord_color_${key}`,
              name: 'color',
              value: key,
              checked: key === ctrl.colorChoice(),
            },
            on: {
              change: e => {
                const target = e.target as HTMLInputElement;
                ctrl.colorChoice(target.value as ColorChoice);
              },
              keyup: ctrl.onRadioInputKeyUp,
            },
          }),
          h(
            `label.color_${key}`,
            {
              attrs: {
                for: `coord_color_${key}`,
                title: ctrl.trans.noarg(i18n),
              },
            },
            h('i')
          ),
        ])
      )
    ),
  ]),
];

const average = (array: number[]) => array.reduce((a, b) => a + b) / array.length;
const scoreCharts = (ctrl: CoordinateTrainerCtrl): VNode =>
  h(
    'div.box',
    h(
      'div.scores',
      [
        ['white', 'averageScoreAsWhiteX', ctrl.modeScores[ctrl.mode()].white],
        ['black', 'averageScoreAsBlackX', ctrl.modeScores[ctrl.mode()].black],
      ].map(([color, transKey, scoreList]: [Color, string, number[]]) =>
        scoreList.length
          ? h('div.color-chart', [
              h('p', ctrl.trans.vdom(transKey, h('strong', `${average(scoreList).toFixed(2)}`))),
              h('svg.sparkline', {
                attrs: {
                  height: '80px',
                  'stroke-width': '3',
                  id: `${color}-sparkline`,
                },
                hook: {
                  insert: vnode => ctrl.updateChart(vnode.elm as SVGSVGElement, color),
                },
              }),
            ])
          : null
      )
    )
  );

const scoreBox = (ctrl: CoordinateTrainerCtrl): VNode =>
  h('div.box.current-status', [h('h1', ctrl.trans('score')), h('div.score', ctrl.score)]);

const timeBox = (ctrl: CoordinateTrainerCtrl): VNode =>
  h('div.box.current-status', [
    h('h1', ctrl.trans('time')),
    h('div.timer', { class: { hurry: ctrl.timeLeft <= 10 * 1000 } }, (ctrl.timeLeft / 1000).toFixed(1)),
  ]);

const backButton = (ctrl: CoordinateTrainerCtrl): VNode =>
  h(
    'div.back',
    h(
      'a.back-button',
      {
        hook: bind('click', ctrl.stop),
      },
      `« ${ctrl.trans('back')}`
    )
  );

const settings = (ctrl: CoordinateTrainerCtrl): VNode => {
  const { trans, redraw, showCoordinates, showPieces } = ctrl;
  return h('div.settings', [
    toggle(
      { name: 'showCoordinates', id: 'showCoordinates', checked: showCoordinates(), change: showCoordinates },
      trans,
      redraw
    ),
    toggle({ name: 'showPieces', id: 'showPieces', checked: showPieces(), change: showPieces }, trans, redraw),
  ]);
};

const side = (ctrl: CoordinateTrainerCtrl): VNode =>
  h('div.side', [
    h('div.box', h('h1', ctrl.trans('coordinates'))),
    ...(ctrl.playing
      ? [
          scoreBox(ctrl),
          !ctrl.timeDisabled() ? timeBox(ctrl) : null,
          ctrl.isAuth && ctrl.hasModeScores() ? scoreCharts(ctrl) : null,
          ctrl.timeDisabled() ? backButton(ctrl) : null,
        ]
      : [
          ctrl.hasPlayed ? scoreBox(ctrl) : null,
          ...configurationButtons(ctrl),
          ctrl.isAuth && ctrl.hasModeScores() ? scoreCharts(ctrl) : null,
          settings(ctrl),
        ]),
  ]);

export default side;
